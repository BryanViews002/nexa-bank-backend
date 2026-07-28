package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "daily_transfer_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyTransferLimit = new BigDecimal("10000.00");

    @Column(name = "daily_withdrawal_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyWithdrawalLimit = new BigDecimal("2000.00");

    @Column(name = "online_transactions_enabled", nullable = false)
    private boolean onlineTransactionsEnabled = true;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    private int version;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum AccountType {
        SAVINGS, CHECKING, GOAL
    }

    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }
}
