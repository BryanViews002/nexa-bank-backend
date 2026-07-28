package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "external_transfers")
public class ExternalTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRail rail;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "provider_reference", unique = true, length = 100)
    private String providerReference;

    /**
     * Counterparty identifier at the far end of the rail. Only the final four characters
     * are retained; the frontend never receives more than that.
     */
    @Column(name = "counterparty_last_four", length = 4)
    private String counterpartyLastFour;

    @Column(name = "counterparty_name", length = 120)
    private String counterpartyName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_transaction_id")
    private Transaction reversalTransaction;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum TransferDirection {
        INBOUND,
        OUTBOUND
    }

    public enum PaymentRail {
        ACH,
        SEPA,
        WIRE,
        CARD_FUNDING
    }

    public enum TransferStatus {
        PENDING,
        PROCESSING,
        SETTLED,
        FAILED,
        RETURNED
    }
}
