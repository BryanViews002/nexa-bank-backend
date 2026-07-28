// src/main/java/com/example/bank/repository/ScheduledPaymentRepository.java
package com.example.bank.repository;

import com.example.bank.entity.ScheduledPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {
    @Query("SELECT s FROM ScheduledPayment s WHERE s.enabled = true AND s.nextRun <= CURRENT_TIMESTAMP")
    List<ScheduledPayment> findDuePayments();

    List<ScheduledPayment> findByAccountFromUserIdOrderByNextRunAsc(Long userId);

    Optional<ScheduledPayment> findByIdAndAccountFromUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ScheduledPayment s WHERE s.id = :id")
    Optional<ScheduledPayment> findByIdForUpdate(@Param("id") Long id);
}
