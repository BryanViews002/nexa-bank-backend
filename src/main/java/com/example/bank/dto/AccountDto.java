// src/main/java/com/example/bank/dto/AccountDto.java
package com.example.bank.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountDto {
    private String type;
    private String currency = "USD";
    private String displayName;
    private BigDecimal dailyTransferLimit;
    private BigDecimal dailyWithdrawalLimit;
}
