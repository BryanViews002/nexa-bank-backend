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

    @Column(length = 500)
    private String description;

    @Column(length = 80)
    private String category = "BILLS";

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "max_failures", nullable = false)
    private int maxFailures = 3;

    @Version
    private int version;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
