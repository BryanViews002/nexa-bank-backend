package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "security_alerts_enabled", nullable = false)
    private boolean securityAlertsEnabled = true;

    @Column(name = "transaction_alerts_enabled", nullable = false)
    private boolean transactionAlertsEnabled = true;

    @Column(name = "budget_alerts_enabled", nullable = false)
    private boolean budgetAlertsEnabled = true;
}
