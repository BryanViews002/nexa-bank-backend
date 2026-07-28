package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class DisputeDto {

    private DisputeDto() {
    }

    public record CreateRequest(
            @NotNull Long transactionId,
            @NotNull String reason,
            @NotBlank @Size(max = 2000) String description
    ) {
    }

    public record AdminUpdateRequest(
            String status,
            @Size(max = 2000) String note
    ) {
    }

    public record ResolveRequest(
            @NotNull Boolean inFavourOfCustomer,
            @NotBlank @Size(max = 2000) String resolutionNote
    ) {
    }

    public record Response(
            Long id,
            String caseReference,
            Long userId,
            String userName,
            Long transactionId,
            String transactionReference,
            String reason,
            String description,
            BigDecimal amount,
            String currency,
            String status,
            boolean provisionalCreditGranted,
            Long provisionalCreditTransactionId,
            Long clawbackTransactionId,
            String resolutionNote,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
