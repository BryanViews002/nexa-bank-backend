package com.example.bank.service;

import com.example.bank.dto.NotificationPreferenceDto;
import com.example.bank.dto.NotificationResponse;
import com.example.bank.entity.Notification;
import com.example.bank.entity.NotificationPreference;
import com.example.bank.entity.User;
import com.example.bank.repository.NotificationPreferenceRepository;
import com.example.bank.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional
    public void notify(
            User user,
            Notification.NotificationType type,
            String title,
            String message,
            String resourceType,
            Long resourceId
    ) {
        NotificationPreference preference = preferenceEntity(user);
        if (!preference.isInAppEnabled()) {
            return;
        }
        if (type == Notification.NotificationType.TRANSACTION && !preference.isTransactionAlertsEnabled()) {
            return;
        }
        if (type == Notification.NotificationType.BUDGET && !preference.isBudgetAlertsEnabled()) {
            return;
        }
        if (type == Notification.NotificationType.SECURITY && !preference.isSecurityAlertsEnabled()) {
            return;
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRelatedResourceType(resourceType);
        notification.setRelatedResourceId(resourceId);
        notificationRepository.save(notification);
    }

    public Page<NotificationResponse> list(User user, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(Long id, User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to update notification");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(User user) {
        notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), Pageable.unpaged())
                .stream()
                .filter(notification -> notification.getReadAt() == null)
                .forEach(notification -> notification.setReadAt(Instant.now()));
    }

    public long unreadCount(User user) {
        return notificationRepository.countByUserIdAndReadAtIsNull(user.getId());
    }

    public NotificationPreferenceDto getPreferences(User user) {
        return toPreferenceDto(preferenceEntity(user));
    }

    @Transactional
    public NotificationPreferenceDto updatePreferences(User user, NotificationPreferenceDto request) {
        NotificationPreference preference = preferenceEntity(user);
        preference.setInAppEnabled(request.inAppEnabled());
        preference.setEmailEnabled(request.emailEnabled());
        preference.setSecurityAlertsEnabled(request.securityAlertsEnabled());
        preference.setTransactionAlertsEnabled(request.transactionAlertsEnabled());
        preference.setBudgetAlertsEnabled(request.budgetAlertsEnabled());
        return toPreferenceDto(notificationPreferenceRepository.save(preference));
    }

    private NotificationPreference preferenceEntity(User user) {
        return notificationPreferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    NotificationPreference preference = new NotificationPreference();
                    preference.setUser(user);
                    return notificationPreferenceRepository.save(preference);
                });
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedResourceType(),
                notification.getRelatedResourceId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private NotificationPreferenceDto toPreferenceDto(NotificationPreference preference) {
        return new NotificationPreferenceDto(
                preference.isInAppEnabled(),
                preference.isEmailEnabled(),
                preference.isSecurityAlertsEnabled(),
                preference.isTransactionAlertsEnabled(),
                preference.isBudgetAlertsEnabled()
        );
    }
}
