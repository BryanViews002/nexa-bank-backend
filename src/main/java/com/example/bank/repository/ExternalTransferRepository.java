package com.example.bank.repository;

import com.example.bank.entity.ExternalTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExternalTransferRepository extends JpaRepository<ExternalTransfer, Long> {
    List<ExternalTransfer> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ExternalTransfer> findByIdAndUserId(Long id, Long userId);

    Optional<ExternalTransfer> findByProviderReference(String providerReference);
}
