package com.example.bank.repository;

import com.example.bank.entity.LedgerJournal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerJournalRepository extends JpaRepository<LedgerJournal, Long> {
    Optional<LedgerJournal> findByTransactionId(Long transactionId);
}
