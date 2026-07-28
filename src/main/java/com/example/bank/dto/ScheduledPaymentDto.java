package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class ScheduledPaymentDto {

    private ScheduledPaymentDto() {
    }

    public record CreateRequest(
            @NotNull Long accountFromId,
            @NotBlank String accountTo,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Min(1) @Max(365) int intervalDays,
            Instant firstRun,
            String description,
            String category
    ) {
    }

    public record UpdateRequest(
            @Min(1) @Max(365) Integer intervalDays,
            Instant nextRun,
            Boolean enabled,
            String description,
            String category,
            @Min(1) @Max(10) Integer maxFailures
    ) {
    }

    public record Response(
            Long id,
            Long accountFromId,
            String maskedDestination,
            BigDecimal amount,
            String currency,
            int intervalDays,
            Instant nextRun,
            Instant lastRun,
            boolean enabled,
            String description,
            String category,
            String lastError,
            int failureCount,
            int maxFailures,
            Instant createdAt
    ) {
    }
}
