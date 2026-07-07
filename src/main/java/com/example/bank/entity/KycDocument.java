// src/main/java/com/example/bank/entity/KycDocument.java
package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "kyc_documents")
public class KycDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String filename;

    private String path;

    private String contentType;

    @Enumerated(EnumType.STRING)
    private KycStatus status = KycStatus.PENDING;

    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = Instant.now();
    }

    public enum KycStatus {
        PENDING, APPROVED, REJECTED
    }
}