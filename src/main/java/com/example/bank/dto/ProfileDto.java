package com.example.bank.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDto {

    private ProfileDto() {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email String email,
            @Size(max = 30) String phoneNumber,
            @Size(max = 500) String address
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword
    ) {
    }
}
