package com.example.bank.repository;

import com.example.bank.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);

    Page<SupportTicket> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
