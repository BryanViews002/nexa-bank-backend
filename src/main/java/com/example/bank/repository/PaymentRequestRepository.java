package com.example.bank.repository;

import com.example.bank.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
    List<PaymentRequest> findByRequesterIdOrderByCreatedAtDesc(Long requesterId);

    List<PaymentRequest> findByPayerIdOrderByCreatedAtDesc(Long payerId);

    Optional<PaymentRequest> findByIdAndPayerId(Long id, Long payerId);

    Optional<PaymentRequest> findByIdAndRequesterId(Long id, Long requesterId);

    List<PaymentRequest> findByStatusAndExpiresAtBefore(PaymentRequest.RequestStatus status, Instant expiresAt);
}
