package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class CardDto {

    private CardDto() {
    }

    public record IssueRequest(
            @NotNull Long accountId,
            @NotNull String type,
            String brand,
            @DecimalMin("1.00") BigDecimal dailyLimit,
            @DecimalMin("1.00") BigDecimal perTransactionLimit
    ) {
    }

    public record ControlRequest(
            @DecimalMin("1.00") BigDecimal dailyLimit,
            @DecimalMin("1.00") BigDecimal perTransactionLimit,
            Boolean contactlessEnabled,
            Boolean onlineEnabled,
            Boolean internationalEnabled
    ) {
    }

    public record PurchaseRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank @Size(max = 120) String merchant,
            @Size(max = 80) String category,
            Boolean online,
            Boolean international
    ) {
    }

    public record Response(
            Long id,
            Long accountId,
            String maskedNumber,
            String lastFour,
            String brand,
            String type,
            String cardHolder,
            String expiry,
            String status,
            BigDecimal dailyLimit,
            BigDecimal perTransactionLimit,
            BigDecimal dailySpendUsed,
            boolean contactlessEnabled,
            boolean onlineEnabled,
            boolean internationalEnabled,
            Instant createdAt
    ) {
    }

    /**
     * Returned exactly once, in the response to the issue call. The full card number is
     * never persisted and can never be retrieved again.
     */
    public record IssuedResponse(
            Response card,
            String cardNumber,
            String cvv
    ) {
    }
}
