package com.example.bank.repository;

import com.example.bank.entity.Account;
import com.example.bank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountOrToAccount(Account fromAccount, Account toAccount);

    List<Transaction> findByFromAccountInOrToAccountIn(List<Account> fromAccounts, List<Account> toAccounts);

    Optional<Transaction> findByInitiatedByUserIdAndTypeAndIdempotencyKey(
            Long initiatedByUserId,
            Transaction.TransactionType type,
            String idempotencyKey
    );

    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.fromAccount = :account OR t.toAccount = :account)
              AND (:type IS NULL OR t.type = :type)
              AND (:status IS NULL OR t.status = :status)
              AND (:category IS NULL OR LOWER(t.category) = LOWER(:category))
              AND (:startDate IS NULL OR t.date >= :startDate)
              AND (:endDate IS NULL OR t.date <= :endDate)
              AND (:minAmount IS NULL OR t.amount >= :minAmount)
              AND (:maxAmount IS NULL OR t.amount <= :maxAmount)
              AND (:query IS NULL OR
                   LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(t.reference) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY t.date DESC
            """)
    Page<Transaction> searchAccountTransactions(
            @Param("account") Account account,
            @Param("type") Transaction.TransactionType type,
            @Param("status") Transaction.TransactionStatus status,
            @Param("category") String category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.fromAccount = :account
              AND t.type IN :types
              AND t.status = com.example.bank.entity.Transaction.TransactionStatus.COMPLETED
              AND t.date >= :fromDate
            """)
    BigDecimal sumCompletedOutgoingSince(
            @Param("account") Account account,
            @Param("types") List<Transaction.TransactionType> types,
            @Param("fromDate") LocalDateTime fromDate
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.fromAccount IN :accounts
              AND t.status = com.example.bank.entity.Transaction.TransactionStatus.COMPLETED
              AND LOWER(t.category) = LOWER(:category)
              AND t.date >= :start
              AND t.date < :end
            """)
    BigDecimal sumSpendByCategory(
            @Param("accounts") List<Account> accounts,
            @Param("category") String category,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Query("""
            SELECT COALESCE(t.category, 'UNCATEGORIZED'), COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.fromAccount IN :accounts
              AND t.status = com.example.bank.entity.Transaction.TransactionStatus.COMPLETED
              AND t.date >= :start
              AND t.date < :end
            GROUP BY COALESCE(t.category, 'UNCATEGORIZED')
            ORDER BY SUM(t.amount) DESC
            """)
    List<Object[]> sumSpendGroupedByCategory(
            @Param("accounts") List<Account> accounts,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<Transaction> findTop10ByFromAccountInOrToAccountInOrderByDateDesc(
            List<Account> fromAccounts,
            List<Account> toAccounts
    );

    List<Transaction> findByFromAccountInAndStatusAndDateBetween(
            List<Account> accounts,
            Transaction.TransactionStatus status,
            LocalDateTime start,
            LocalDateTime end
    );
}
