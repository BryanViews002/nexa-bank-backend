package com.example.bank.service;

import com.example.bank.entity.IdempotencyRecord;
import com.example.bank.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String normalized = key.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key cannot exceed 100 characters");
        }
        return normalized;
    }

    public Optional<Long> findExisting(
            Long userId,
            String operation,
            String key,
            String requestPayload
    ) {
        String requestHash = hash(requestPayload);
        return idempotencyRecordRepository
                .findByUserIdAndOperationAndIdempotencyKey(userId, operation, key)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        throw new IllegalStateException(
                                "The Idempotency-Key was already used with a different request"
                        );
                    }
                    return record.getResourceId();
                });
    }

    @Transactional
    public void record(
            Long userId,
            String operation,
            String key,
            String requestPayload,
            String resourceType,
            Long resourceId
    ) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setUserId(userId);
        record.setOperation(operation);
        record.setIdempotencyKey(key);
        record.setRequestHash(hash(requestPayload));
        record.setResourceType(resourceType);
        record.setResourceId(resourceId);
        idempotencyRecordRepository.save(record);
    }

    private String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
