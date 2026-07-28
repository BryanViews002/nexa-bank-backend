package com.example.bank.repository;

import com.example.bank.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    List<Account> findAllByUserId(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findFirstByUserUsernameAndStatusOrderByCreatedAtAsc(
            String username,
            Account.AccountStatus status
    );

    @Query("""
            SELECT a FROM Account a
            WHERE a.status = com.example.bank.entity.Account.AccountStatus.ACTIVE
              AND a.user.id <> :currentUserId
              AND (
                    LOWER(a.user.username) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(a.user.fullName) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR a.accountNumber LIKE CONCAT('%', :query, '%')
              )
            ORDER BY a.user.fullName, a.createdAt
            """)
    List<Account> searchRecipientAccounts(
            @Param("currentUserId") Long currentUserId,
            @Param("query") String query,
            Pageable pageable
    );
}
