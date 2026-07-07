package com.example.bank.service;

import com.example.bank.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OtpService {

    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final int OTP_EXPIRY_MINUTES = 5;
    private final String activeProfile;

    public OtpService(@Value("${spring.profiles.active:prod}") String activeProfile) {
        this.activeProfile = activeProfile;
    }

    /**
     * Generates a 6-digit OTP for the given user and purpose
     */
    public void generateOtp(User user, String purpose) {
        String code = String.format("%06d", random.nextInt(1000000));
        String key = user.getId() + "_" + purpose;

        OtpData otpData = new OtpData(code, LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpStore.put(key, otpData);

        // Log OTP in dev mode for frontend or console access
        if ("dev".equals(activeProfile)) {
            log.info("🔐 [DEV] Generated OTP for user '{}' (ID: {}) with purpose '{}': {}",
                    user.getUsername(), user.getId(), purpose, code);
            log.debug("OTP expires at: {}", otpData.getExpiryTime());
        }
    }

    /**
     * Verifies the OTP for the given user and purpose
     */
    public boolean verifyOtp(User user, String code, String purpose) {
        String key = user.getId() + "_" + purpose;
        OtpData otpData = otpStore.get(key);

        log.info("🔍 OTP verification attempt for user '{}' with purpose '{}', provided code: {}",
                user.getUsername(), purpose, code);

        if (otpData == null) {
            log.warn("❌ No OTP found for user '{}' with purpose '{}'", user.getUsername(), purpose);
            return false;
        }

        if (LocalDateTime.now().isAfter(otpData.getExpiryTime())) {
            log.warn("⏰ OTP expired for user '{}' with purpose '{}'. Expired at: {}",
                    user.getUsername(), purpose, otpData.getExpiryTime());
            otpStore.remove(key);
            return false;
        }

        if (otpData.getCode().equals(code)) {
            log.info("✅ OTP verification successful for user '{}' with purpose '{}'",
                    user.getUsername(), purpose);
            otpStore.remove(key);
            return true;
        }

        log.warn("❌ Invalid OTP provided for user '{}' with purpose '{}'. Expected: {}, Provided: {}",
                user.getUsername(), purpose, otpData.getCode(), code);
        return false;
    }

    /**
     * Retrieves the latest OTP for the given user and purpose (dev mode only)
     */
    public String getLatestOtp(User user, String purpose) {
        if (!"dev".equals(activeProfile)) {
            log.warn("Attempt to access OTP retrieval in non-dev profile for user: {}", user.getUsername());
            throw new UnsupportedOperationException("OTP retrieval is only available in dev profile");
        }
        String key = user.getId() + "_" + purpose;
        OtpData otpData = otpStore.get(key);
        if (otpData == null || LocalDateTime.now().isAfter(otpData.getExpiryTime())) {
            log.warn("No valid OTP found for user '{}' with purpose '{}'", user.getUsername(), purpose);
            return null;
        }
        log.info("Retrieved OTP for user '{}' with purpose '{}': {}", user.getUsername(), purpose, otpData.getCode());
        return otpData.getCode();
    }

    /**
     * Removes expired OTPs from the store
     */
    public void cleanupExpiredOtps() {
        int initialSize = otpStore.size();
        otpStore.entrySet().removeIf(entry ->
                LocalDateTime.now().isAfter(entry.getValue().getExpiryTime()));
        int removedCount = initialSize - otpStore.size();
        if (removedCount > 0) {
            log.debug("🧹 Cleaned up {} expired OTPs. Remaining: {}", removedCount, otpStore.size());
        }
    }

    /**
     * Development helper method to see all active OTPs
     */
    public void logActiveOtps() {
        if ("dev".equals(activeProfile)) {
            log.info("📊 Active OTPs in store: {}", otpStore.size());
            otpStore.forEach((key, otpData) ->
                    log.info("  Key: {} -> Code: {}, Expires: {}", key, otpData.getCode(), otpData.getExpiryTime()));
        }
    }

    /**
     * Inner class to hold OTP data
     */
    private static class OtpData {
        private final String code;
        private final LocalDateTime expiryTime;

        public OtpData(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }

        public String getCode() {
            return code;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }
    }
}