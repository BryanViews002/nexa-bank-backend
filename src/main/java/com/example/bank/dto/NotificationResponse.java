package com.example.bank.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String relatedResourceType,
        Long relatedResourceId,
        Instant readAt,
        Instant createdAt
) {
}
