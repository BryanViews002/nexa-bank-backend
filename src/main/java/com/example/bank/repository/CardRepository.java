package com.example.bank.repository;

import com.example.bank.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Card> findByIdAndUserId(Long id, Long userId);

    List<Card> findByAccountId(Long accountId);

    boolean existsByCardNumberHash(String cardNumberHash);
}
