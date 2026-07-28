package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BudgetDto {

    private BudgetDto() {
    }

    public record UpsertRequest(
            @NotBlank @Size(max = 80) String category,
            @NotNull @DecimalMin("0.01") BigDecimal monthlyLimit,
            @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal alertThreshold,
            LocalDate periodStart
    ) {
    }

    public record UpdateRequest(
            @DecimalMin("0.01") BigDecimal monthlyLimit,
            @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal alertThreshold,
            Boolean active
    ) {
    }

    public record Response(
            Long id,
            String category,
            BigDecimal monthlyLimit,
            BigDecimal spent,
            BigDecimal remaining,
            BigDecimal usagePercent,
            BigDecimal alertThreshold,
            boolean overLimit,
            boolean thresholdReached,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            boolean active,
            Instant createdAt
    ) {
    }

    public record CategorySpend(
            String category,
            BigDecimal amount,
            BigDecimal sharePercent
    ) {
    }

    public record Summary(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal totalBudgeted,
            BigDecimal totalSpent,
            BigDecimal totalRemaining,
            List<Response> budgets,
            List<CategorySpend> spendByCategory
    ) {
    }
}
