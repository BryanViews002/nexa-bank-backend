// AuditService.java (Updated)
package com.example.bank.service;

import com.example.bank.entity.AuditLog;
import com.example.bank.repository.AuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditLogRepository) {
        this.auditRepository = auditLogRepository;
    }

    /**
     * Logs an audit event for the specified user
     */
    public void log(Long userId, String action, Map<String, Object> details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setDetails(details.toString());
        auditLog.setTimestamp(LocalDateTime.now());

        auditRepository.save(auditLog);

        // Also log to console for development
        System.out.println(String.format(
                "[AUDIT] %s - User ID: %d, Action: %s, Details: %s",
                LocalDateTime.now(),
                userId,
                action,
                details
        ));
    }

    /**
     * Retrieves audit logs with optional filters
     */
    public List<AuditLog> getLogs(Map<String, String> params) {
        Long userId = params.containsKey("userId") ? Long.valueOf(params.get("userId")) : null;
        String action = params.get("action");
        String startDateStr = params.get("startDate");
        String endDateStr = params.get("endDate");
        String limitStr = params.get("limit");

        LocalDateTime startDate = null;
        LocalDateTime endDate = null;

        if (startDateStr != null && !startDateStr.isEmpty()) {
            startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
        }
        if (endDateStr != null && !endDateStr.isEmpty()) {
            endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
        }

        // Apply filters
        List<AuditLog> logs = auditRepository.findLogsWithFilters(userId, action, startDate, endDate);

        // Apply limit if specified
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                int limit = Integer.parseInt(limitStr);
                return logs.stream().limit(limit).toList();
            } catch (NumberFormatException e) {
                // If limit is invalid, return all logs
            }
        }

        return logs;
    }

    /**
     * Get recent audit logs with pagination
     */
    public List<AuditLog> getRecentLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return auditRepository.findAllByOrderByTimestampDesc(pageable).getContent();
    }

    /**
     * Get audit logs for a specific user
     */
    public List<AuditLog> getLogsByUser(Long userId) {
        return auditRepository.findByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Get audit logs for a specific action
     */
    public List<AuditLog> getLogsByAction(String action) {
        return auditRepository.findByActionOrderByTimestampDesc(action);
    }
}