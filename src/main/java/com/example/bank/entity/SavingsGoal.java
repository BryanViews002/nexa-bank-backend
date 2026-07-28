package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funding_account_id", nullable = false)
    private Account fundingAccount;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_account_id", nullable = false, unique = true)
    private Account goalAccount;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "auto_contribution_amount", precision = 19, scale = 4)
    private BigDecimal autoContributionAmount;

    @Column(name = "auto_contribution_interval_days")
    private Integer autoContributionIntervalDays;

    @Column(name = "next_auto_contribution")
    private Instant nextAutoContribution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum GoalStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
