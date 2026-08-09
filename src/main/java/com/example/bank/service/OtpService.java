package com.example.bank.service;

import com.example.bank.entity.Otp;
import com.example.bank.entity.User;
import com.example.bank.repository.OtpRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int MAX_GENERATIONS_PER_TEN_MINUTES = 3;

    private final OtpRepository otpRepository;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, DevOtp> devOtpStore = new ConcurrentHashMap<>();
    private final String activeProfile;
    private final String otpPepper;

    public OtpService(
            OtpRepository otpRepository,
            @Value("${spring.profiles.active:prod}") String activeProfile,
            @Value("${app.security.otp-pepper}") String otpPepper
    ) {
        this.otpRepository = otpRepository;
        this.activeProfile = activeProfile;
        this.otpPepper = otpPepper;
    }

    @Transactional
    public String generateOtp(User user, String purposeValue) {
        Otp.OtpPurpose purpose = parsePurpose(purposeValue);
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        long recentCodes = otpRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                purpose,
                tenMinutesAgo
        );
        if (recentCodes >= MAX_GENERATIONS_PER_TEN_MINUTES) {
            throw new IllegalStateException("Too many OTP requests. Try again later.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        Otp otp = new Otp();
        otp.setUser(user);
        otp.setPurpose(purpose);
        otp.setCode(hash(user.getId(), purpose, code));
        otp.setExpiresAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        otp.setUsed(false);
        otp.setFailedAttempts(0);
        otpRepository.save(otp);

        if (isDev()) {
            devOtpStore.put(key(user.getId(), purpose), new DevOtp(code, otp.getExpiresAt()));
            log.info("[DEV] OTP generated for user ID {} and purpose {}", user.getId(), purpose);
        }
        return code;
    }

    @Transactional
    public boolean verifyOtp(User user, String code, String purposeValue) {
        Otp.OtpPurpose purpose = parsePurpose(purposeValue);
        Otp otp = otpRepository.findLatestValidOtp(user.getId(), purpose, Instant.now()).orElse(null);
        if (otp == null) {
            return false;
        }

        if (otp.getFailedAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return false;
        }

        boolean matches = MessageDigest.isEqual(
                otp.getCode().getBytes(StandardCharsets.UTF_8),
                hash(user.getId(), purpose, code).getBytes(StandardCharsets.UTF_8)
        );
        if (matches) {
            otp.setUsed(true);
            otpRepository.save(otp);
            devOtpStore.remove(key(user.getId(), purpose));
            return true;
        }

        otp.setFailedAttempts(otp.getFailedAttempts() + 1);
        if (otp.getFailedAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            otp.setUsed(true);
        }
        otpRepository.save(otp);
        return false;
    }

    public String getLatestOtp(User user, String purposeValue) {
        if (!isDev()) {
            throw new UnsupportedOperationException("OTP retrieval is only available in the dev profile");
        }
        Otp.OtpPurpose purpose = parsePurpose(purposeValue);
        DevOtp otp = devOtpStore.get(key(user.getId(), purpose));
        if (otp == null || otp.expiresAt().isBefore(Instant.now())) {
            devOtpStore.remove(key(user.getId(), purpose));
            return null;
        }
        return otp.code();
    }

    @Scheduled(fixedDelayString = "${app.security.otp-cleanup-ms:60000}")
    public void cleanupExpiredOtps() {
        devOtpStore.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
    }

    private Otp.OtpPurpose parsePurpose(String purpose) {
        try {
            return Otp.OtpPurpose.valueOf(purpose.toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unsupported OTP purpose");
        }
    }

    private String hash(Long userId, Otp.OtpPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otpPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String value = userId + ":" + purpose + ":" + code;
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to secure OTP", e);
        }
    }

    private String key(Long userId, Otp.OtpPurpose purpose) {
        return userId + ":" + purpose;
    }

    private boolean isDev() {
        return "dev".equalsIgnoreCase(activeProfile);
    }

    private record DevOtp(String code, Instant expiresAt) {
    }
}
