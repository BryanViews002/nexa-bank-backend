package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class ExternalTransferDto {

    private ExternalTransferDto() {
    }

    public record InitiateRequest(
            @NotNull Long accountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull String rail,
            @NotBlank @Size(max = 64) String counterpartyReference,
            @Size(max = 120) String counterpartyName
    ) {
    }

    public record Response(
            Long id,
            Long accountId,
            String direction,
            String rail,
            String provider,
            String providerReference,
            String counterpartyMasked,
            String counterpartyName,
            BigDecimal amount,
            BigDecimal fee,
            String currency,
            String status,
            String failureReason,
            Long transactionId,
            Instant settledAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /**
     * Provider callback body. {@code event} is one of
     * {@code transfer.settled}, {@code transfer.failed}, {@code transfer.returned}.
     */
    public record WebhookEvent(
            @NotBlank String event,
            @NotBlank String providerReference,
            String reason
    ) {
    }
}
