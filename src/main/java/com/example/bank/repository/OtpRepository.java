// src/main/java/com/example/bank/repository/OtpRepository.java
package com.example.bank.repository;

import com.example.bank.entity.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    @Query("SELECT o FROM Otp o WHERE o.user.id = :userId AND o.purpose = :purpose AND o.used = false AND o.expiresAt > CURRENT_TIMESTAMP ORDER BY o.createdAt DESC")
    Optional<Otp> findLatestValidOtp(@Param("userId") Long userId, @Param("purpose") Otp.OtpPurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(
            Long userId,
            Otp.OtpPurpose purpose,
            Instant createdAt
    );
}
