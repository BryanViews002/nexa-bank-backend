package com.example.bank.repository;

import com.example.bank.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByUserIdOrderByLastUsedAtDescCreatedAtDesc(Long userId);

    Optional<Beneficiary> findByIdAndUserId(Long id, Long userId);
}
