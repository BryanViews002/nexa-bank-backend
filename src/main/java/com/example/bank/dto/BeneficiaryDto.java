package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class BeneficiaryDto {

    private BeneficiaryDto() {
    }

    public record CreateRequest(
            @NotBlank String recipientIdentifier,
            String nickname
    ) {
    }

    public record Response(
            Long id,
            String name,
            String username,
            String maskedAccountNumber,
            String nickname,
            boolean active,
            Instant verifiedAt,
            Instant lastUsedAt,
            Instant createdAt
    ) {
    }
}
