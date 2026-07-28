package com.example.bank.repository;

import com.example.bank.entity.LedgerPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, Long> {
    List<LedgerPosting> findByJournalIdOrderByIdAsc(Long journalId);
}
