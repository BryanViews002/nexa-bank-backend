package com.example.bank.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class TransferDto {
    @NotNull(message = "From account ID is required")
    private Long fromAccountId;

    @NotBlank(message = "Recipient identifier is required")
    private String toIdentifier; // Username or account number

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private double amount;

    // Constructors
    public TransferDto() {}

    public TransferDto(Long fromAccountId, String toIdentifier, double amount) {
        this.fromAccountId = fromAccountId;
        this.toIdentifier = toIdentifier;
        this.amount = amount;
    }

    // Getters and Setters
    public Long getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(Long fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public String getToIdentifier() {
        return toIdentifier;
    }

    public void setToIdentifier(String toIdentifier) {
        this.toIdentifier = toIdentifier;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }


}