package com.example.bank.repository;

import com.example.bank.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(
            Long userId,
            String operation,
            String idempotencyKey
    );
}
