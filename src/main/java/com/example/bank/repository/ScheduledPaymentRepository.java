// src/main/java/com/example/bank/repository/ScheduledPaymentRepository.java
package com.example.bank.repository;

import com.example.bank.entity.ScheduledPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {
    @Query("SELECT s FROM ScheduledPayment s WHERE s.enabled = true AND s.nextRun <= CURRENT_TIMESTAMP")
    List<ScheduledPayment> findDuePayments();
}