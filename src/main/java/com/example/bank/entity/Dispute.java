package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_reference", nullable = false, unique = true, length = 40)
    private String caseReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DisputeReason reason;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "provisional_credit_granted", nullable = false)
    private boolean provisionalCreditGranted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provisional_credit_transaction_id")
    private Transaction provisionalCreditTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clawback_transaction_id")
    private Transaction clawbackTransaction;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_admin_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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

    public enum DisputeReason {
        UNAUTHORIZED,
        DUPLICATE_CHARGE,
        PRODUCT_NOT_RECEIVED,
        PRODUCT_UNACCEPTABLE,
        INCORRECT_AMOUNT,
        CANCELLED_RECURRING,
        OTHER
    }

    public enum DisputeStatus {
        OPEN,
        UNDER_REVIEW,
        EVIDENCE_REQUESTED,
        RESOLVED_CUSTOMER,
        RESOLVED_MERCHANT,
        WITHDRAWN
    }
}
