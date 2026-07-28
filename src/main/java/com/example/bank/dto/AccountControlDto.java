package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class AccountControlDto {

    private AccountControlDto() {
    }

    public record UpdateRequest(
            @Size(max = 80) String displayName,
            @DecimalMin("0.01") BigDecimal dailyTransferLimit,
            @DecimalMin("0.01") BigDecimal dailyWithdrawalLimit,
            Boolean onlineTransactionsEnabled
    ) {
    }
}
