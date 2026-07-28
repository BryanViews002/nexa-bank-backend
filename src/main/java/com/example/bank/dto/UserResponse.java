package com.example.bank.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String address,
        String role,
        String kycStatus,
        boolean enabled,
        boolean locked,
        LocalDateTime createdAt
) {
}
