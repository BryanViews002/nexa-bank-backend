package com.example.bank.repository;

import com.example.bank.entity.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavingsGoal> findByIdAndUserId(Long id, Long userId);

    List<SavingsGoal> findByStatusAndNextAutoContributionLessThanEqual(
            SavingsGoal.GoalStatus status,
            Instant nextAutoContribution
    );
}
