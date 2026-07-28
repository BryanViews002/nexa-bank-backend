package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transactions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_transaction_user_operation_idempotency",
                columnNames = {"initiated_by_user_id", "type", "idempotency_key"}
        )
})
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_uuid", nullable = false, unique = true, length = 36)
    private String txUuid;

    @ManyToOne
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(name = "to_external_account")
    private String toExternalAccount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(length = 500)
    private String description;

    @Column(length = 80)
    private String category;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    @Column(name = "exchange_rate", precision = 19, scale = 8)
    private BigDecimal exchangeRate;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime date = LocalDateTime.now();

    @PrePersist
    public void generateUuid() {
        if (this.txUuid == null) {
            this.txUuid = UUID.randomUUID().toString();
        }
        if (this.reference == null) {
            this.reference = "NX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAW,
        TRANSFER,
        BONUS,
        SCHEDULED_PAYMENT,
        GOAL_CONTRIBUTION,
        GOAL_WITHDRAWAL,
        CARD_PURCHASE,
        EXTERNAL_FUNDING,
        EXTERNAL_PAYOUT,
        LOAN_DISBURSEMENT,
        LOAN_REPAYMENT,
        FX_EXCHANGE,
        PAYMENT_REQUEST,
        REVERSAL
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }
}
