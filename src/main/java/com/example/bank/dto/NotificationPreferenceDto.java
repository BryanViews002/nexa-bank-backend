package com.example.bank.dto;

public record NotificationPreferenceDto(
        boolean inAppEnabled,
        boolean emailEnabled,
        boolean securityAlertsEnabled,
        boolean transactionAlertsEnabled,
        boolean budgetAlertsEnabled
) {
}
