package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "beneficiaries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_beneficiary_user_account", columnNames = {"user_id", "account_number"})
})
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "account_number", nullable = false, length = 255)
    private String accountNumber;

    @Column(name = "recipient_username", length = 50)
    private String recipientUsername;

    @Column(length = 80)
    private String nickname;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
