package com.example.bank.repository;

import com.example.bank.entity.Dispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    List<Dispute> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Dispute> findByIdAndUserId(Long id, Long userId);

    Page<Dispute> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Dispute> findByStatusOrderByCreatedAtDesc(Dispute.DisputeStatus status, Pageable pageable);

    boolean existsByTransactionIdAndStatusNotIn(Long transactionId, List<Dispute.DisputeStatus> statuses);
}
