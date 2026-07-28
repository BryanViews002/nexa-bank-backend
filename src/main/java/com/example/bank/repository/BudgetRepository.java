package com.example.bank.repository;

import com.example.bank.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserIdOrderByCategoryAsc(Long userId);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    Optional<Budget> findByUserIdAndCategoryAndPeriodStart(Long userId, String category, LocalDate periodStart);

    List<Budget> findByUserIdAndPeriodStartOrderByCategoryAsc(Long userId, LocalDate periodStart);
}
