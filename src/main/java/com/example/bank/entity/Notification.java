package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "related_resource_type", length = 60)
    private String relatedResourceType;

    @Column(name = "related_resource_id")
    private Long relatedResourceId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public enum NotificationType {
        TRANSACTION,
        SECURITY,
        KYC,
        SCHEDULED_PAYMENT,
        SAVINGS_GOAL,
        BUDGET,
        PAYMENT_REQUEST,
        SUPPORT,
        CARD,
        LOAN,
        DISPUTE,
        SYSTEM
    }
}
