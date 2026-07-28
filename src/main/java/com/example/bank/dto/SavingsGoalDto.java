package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class SavingsGoalDto {

    private SavingsGoalDto() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("1.00") BigDecimal targetAmount,
            LocalDate targetDate,
            @NotNull Long fundingAccountId,
            @DecimalMin("0.01") BigDecimal autoContributionAmount,
            @Min(1) @Max(365) Integer autoContributionIntervalDays,
            @DecimalMin("0.00") BigDecimal initialContribution
    ) {
    }

    public record UpdateRequest(
            @Size(max = 120) String name,
            @Size(max = 500) String description,
            @DecimalMin("1.00") BigDecimal targetAmount,
            LocalDate targetDate,
            @DecimalMin("0.01") BigDecimal autoContributionAmount,
            @Min(1) @Max(365) Integer autoContributionIntervalDays
    ) {
    }

    public record AmountRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount
    ) {
    }

    public record Response(
            Long id,
            String name,
            String description,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            BigDecimal remainingAmount,
            BigDecimal progressPercent,
            String currency,
            LocalDate targetDate,
            Long fundingAccountId,
            Long goalAccountId,
            String goalAccountNumber,
            BigDecimal autoContributionAmount,
            Integer autoContributionIntervalDays,
            Instant nextAutoContribution,
            String status,
            Instant createdAt
    ) {
    }
}
