// src/main/java/com/example/bank/entity/ScheduledPayment.java
package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Entity
@Table(name = "scheduled_payments")
public class ScheduledPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "account_from_id", nullable = false)
    private Account accountFrom;

    private String accountTo;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    private String currency = "USD";

    private int intervalDays;

    private Instant nextRun;

    private boolean enabled = true;

    private Instant lastRun;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}