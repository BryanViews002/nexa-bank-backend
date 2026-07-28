package com.example.bank.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String accountNumber,
        String maskedAccountNumber,
        String displayName,
        String type,
        String status,
        BigDecimal balance,
        String currency,
        BigDecimal dailyTransferLimit,
        BigDecimal dailyWithdrawalLimit,
        boolean onlineTransactionsEnabled,
        Instant createdAt
) {
}
