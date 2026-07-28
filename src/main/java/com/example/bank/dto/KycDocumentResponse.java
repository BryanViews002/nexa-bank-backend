package com.example.bank.dto;

import java.time.Instant;

public record KycDocumentResponse(
        Long id,
        Long userId,
        String filename,
        String contentType,
        String status,
        String rejectionReason,
        Instant uploadedAt,
        Instant reviewedAt
) {
}
