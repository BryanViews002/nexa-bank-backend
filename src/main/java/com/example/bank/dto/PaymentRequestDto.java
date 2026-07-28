package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentRequestDto {

    private PaymentRequestDto() {
    }

    public record CreateRequest(
            @NotNull Long requesterAccountId,
            @NotBlank String payerIdentifier,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 500) String note,
            @Min(1) @Max(90) Integer expiresInDays
    ) {
    }

    public record AcceptRequest(
            @NotNull Long payerAccountId
    ) {
    }

    public record Response(
            Long id,
            String direction,
            Long requesterUserId,
            String requesterName,
            Long requesterAccountId,
            String requesterAccountNumber,
            Long payerUserId,
            String payerName,
            BigDecimal amount,
            String currency,
            String note,
            String status,
            Instant expiresAt,
            Instant respondedAt,
            Long transactionId,
            Instant createdAt
    ) {
    }
}
