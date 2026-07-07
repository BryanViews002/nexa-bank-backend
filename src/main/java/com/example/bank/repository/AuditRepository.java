package com.example.bank.repository;

import com.example.bank.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs ordered by timestamp descending with pagination
     */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Find audit logs by user ID ordered by timestamp descending
     */
    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * Find audit logs by action ordered by timestamp descending
     */
    List<AuditLog> findByActionOrderByTimestampDesc(String action);

    /**
     * Custom query to find logs with optional filters
     * This method handles the complex filtering logic used in AuditService.getLogs()
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
            "(:userId IS NULL OR a.userId = :userId) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:startDate IS NULL OR a.timestamp >= :startDate) AND " +
            "(:endDate IS NULL OR a.timestamp <= :endDate) " +
            "ORDER BY a.timestamp DESC")
    List<AuditLog> findLogsWithFilters(@Param("userId") Long userId,
                                       @Param("action") String action,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Find logs by date range
     */
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find logs by user and action
     */
    List<AuditLog> findByUserIdAndActionOrderByTimestampDesc(Long userId, String action);

    /**
     * Count logs by user ID
     */
    long countByUserId(Long userId);

    /**
     * Count logs by action
     */
    long countByAction(String action);
}