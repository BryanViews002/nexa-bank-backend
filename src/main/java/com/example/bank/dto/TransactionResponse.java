package com.example.bank.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String reference,
        String transactionUuid,
        Long fromAccountId,
        String fromAccountNumber,
        Long toAccountId,
        String toAccountNumber,
        String externalAccount,
        BigDecimal amount,
        BigDecimal fee,
        String currency,
        String type,
        String status,
        String description,
        String category,
        BigDecimal exchangeRate,
        LocalDateTime createdAt
) {
}
