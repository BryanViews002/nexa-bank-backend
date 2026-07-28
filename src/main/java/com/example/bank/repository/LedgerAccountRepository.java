package com.example.bank.repository;

import com.example.bank.entity.LedgerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {
    Optional<LedgerAccount> findByCode(String code);

    Optional<LedgerAccount> findByCustomerAccountId(Long accountId);
}
