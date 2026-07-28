package com.example.bank.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        String kycStatus,
        Map<String, BigDecimal> balancesByCurrency,
        List<AccountResponse> accounts,
        List<TransactionResponse> recentTransactions,
        List<ScheduledPaymentDto.Response> upcomingPayments,
        long unreadNotifications,
        Instant generatedAt
) {
}
