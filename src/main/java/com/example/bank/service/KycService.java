package com.example.bank.service;

import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.repository.KycRepository;
import com.example.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class KycService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    private final KycRepository kycRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final Path uploadRoot;
    private final long maxFileSize;

    public KycService(
            KycRepository kycRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") String maxFileSize
    ) {
        this.kycRepository = kycRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = DataSize.parse(maxFileSize).toBytes();
    }

    @Transactional
    public KycDocument upload(MultipartFile file, User user) throws IOException {
        validateFile(file);
        Path userDirectory = uploadRoot.resolve(user.getId().toString()).normalize();
        if (!userDirectory.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        Files.createDirectories(userDirectory);

        String originalName = Path.of(file.getOriginalFilename()).getFileName().toString();
        String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        String storedName = UUID.randomUUID() + "-" + safeName;
        Path target = userDirectory.resolve(storedName).normalize();
        if (!target.startsWith(userDirectory)) {
            throw new IllegalArgumentException("Invalid filename");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        KycDocument document = new KycDocument();
        document.setUser(user);
        document.setFilename(storedName);
        document.setPath(target.toString());
        document.setContentType(file.getContentType().toLowerCase(Locale.ROOT));
        document.setStatus(KycDocument.KycStatus.PENDING);
        document = kycRepository.save(document);

        user.setKycStatus(User.KycStatus.PENDING);
        userRepository.save(user);
        notificationService.notify(
                user,
                com.example.bank.entity.Notification.NotificationType.KYC,
                "KYC document received",
                "Your identity document is pending review.",
                "KYC_DOCUMENT",
                document.getId()
        );
        return document;
    }

    @Transactional
    public KycDocument approve(Long id, User reviewer) {
        KycDocument document = getDocument(id);
        document.setStatus(KycDocument.KycStatus.APPROVED);
        document.setRejectionReason(null);
        document.setReviewedAt(Instant.now());
        document.setReviewedByUserId(reviewer == null ? null : reviewer.getId());
        kycRepository.save(document);

        User user = document.getUser();
        user.setKycStatus(User.KycStatus.APPROVED);
        userRepository.save(user);
        notificationService.notify(
                user,
                com.example.bank.entity.Notification.NotificationType.KYC,
                "KYC approved",
                "Your identity verification was approved. Banking features are now available.",
                "KYC_DOCUMENT",
                document.getId()
        );
        return document;
    }

    public void approve(Long id) {
        approve(id, null);
    }

    @Transactional
    public KycDocument reject(Long id, String reason, User reviewer) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        KycDocument document = getDocument(id);
        document.setStatus(KycDocument.KycStatus.REJECTED);
        document.setRejectionReason(reason.trim());
        document.setReviewedAt(Instant.now());
        document.setReviewedByUserId(reviewer == null ? null : reviewer.getId());
        kycRepository.save(document);

        User user = document.getUser();
        user.setKycStatus(User.KycStatus.REJECTED);
        userRepository.save(user);
        notificationService.notify(
                user,
                com.example.bank.entity.Notification.NotificationType.KYC,
                "KYC needs attention",
                "Your identity document was rejected: " + reason.trim(),
                "KYC_DOCUMENT",
                document.getId()
        );
        return document;
    }

    public void reject(Long id) {
        reject(id, "Rejected by administrator", null);
    }

    public List<KycDocument> getPendingKyc() {
        return kycRepository.findByStatus(KycDocument.KycStatus.PENDING);
    }

    public List<KycDocument> getUserDocuments(User user) {
        return kycRepository.findByUserIdOrderByUploadedAtDesc(user.getId());
    }

    public KycDocument getDocument(Long id) {
        return kycRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("KYC document not found"));
    }

    public Resource loadDocument(Long id) throws IOException {
        KycDocument document = getDocument(id);
        Path path = Path.of(document.getPath()).toAbsolutePath().normalize();
        if (!path.startsWith(uploadRoot) || !Files.exists(path)) {
            throw new IOException("Stored KYC document is unavailable");
        }
        return new UrlResource(path.toUri());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty KYC document is required");
        }
        if (file.getSize() > maxFileSize) {
            throw new MaxUploadSizeExceededException(maxFileSize);
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("The uploaded document must have a filename");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only PDF, JPEG, and PNG KYC documents are accepted");
        }
    }
}
