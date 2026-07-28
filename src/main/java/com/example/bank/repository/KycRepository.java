// src/main/java/com/example/bank/repository/KycRepository.java
package com.example.bank.repository;

import com.example.bank.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KycRepository extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findByStatus(KycDocument.KycStatus status);

    List<KycDocument> findByUserIdOrderByUploadedAtDesc(Long userId);
}
