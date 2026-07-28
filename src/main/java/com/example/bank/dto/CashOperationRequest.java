package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CashOperationRequest(
        @NotNull Long accountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String description,
        String category
) {
}
