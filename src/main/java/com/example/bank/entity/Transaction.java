package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transactions")
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

    @Column(nullable = false)
    private double amount;

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
    }

    public enum TransactionType {
        DEPOSIT, WITHDRAW, TRANSFER, BONUS
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED
    }
}