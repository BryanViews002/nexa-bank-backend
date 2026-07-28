package com.example.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SupportDto {

    private SupportDto() {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(max = 160) String subject,
            @NotNull String category,
            String priority,
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    public record MessageRequest(
            @NotBlank @Size(max = 4000) String body,
            Boolean internalNote
    ) {
    }

    public record AdminUpdateRequest(
            String status,
            String priority,
            Long assignedAdminId,
            @Size(max = 1000) String resolution
    ) {
    }

    public record MessageResponse(
            Long id,
            Long authorUserId,
            String authorName,
            boolean fromSupport,
            boolean internalNote,
            String body,
            Instant createdAt
    ) {
    }

    public record TicketResponse(
            Long id,
            Long userId,
            String userName,
            String subject,
            String category,
            String priority,
            String status,
            String resolution,
            Long assignedAdminId,
            String assignedAdminName,
            int messageCount,
            List<MessageResponse> messages,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
