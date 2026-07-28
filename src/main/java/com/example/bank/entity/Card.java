package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

@Data
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * The primary account number is never persisted. Only a salted digest (for uniqueness
     * checks) and the final four digits (for display) are retained.
     */
    @Column(name = "card_number_hash", nullable = false, unique = true, length = 64)
    private String cardNumberHash;

    @Column(name = "last_four", nullable = false, length = 4)
    private String lastFour;

    @Column(name = "cvv_hash", nullable = false)
    private String cvvHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType type;

    @Column(name = "card_holder", nullable = false, length = 100)
    private String cardHolder;

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status = CardStatus.ACTIVE;

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyLimit = new BigDecimal("2000.0000");

    @Column(name = "per_transaction_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal perTransactionLimit = new BigDecimal("1000.0000");

    @Column(name = "contactless_enabled", nullable = false)
    private boolean contactlessEnabled = true;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled = true;

    @Column(name = "international_enabled", nullable = false)
    private boolean internationalEnabled;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return YearMonth.of(expiryYear, expiryMonth).isBefore(YearMonth.now());
    }

    public enum CardBrand {
        VISA,
        MASTERCARD
    }

    public enum CardType {
        DEBIT,
        VIRTUAL
    }

    public enum CardStatus {
        ACTIVE,
        FROZEN,
        CANCELLED,
        EXPIRED
    }
}
