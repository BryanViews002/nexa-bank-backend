// src/main/java/com/example/bank/entity/Otp.java
package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "otps")
public class Otp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String code;

    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    private Instant expiresAt;

    private boolean used;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public enum OtpPurpose {
        LOGIN, PASSWORD_RESET
    }
}