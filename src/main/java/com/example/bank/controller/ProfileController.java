package com.example.bank.controller;

import com.example.bank.dto.ProfileDto;
import com.example.bank.dto.UserResponse;
import com.example.bank.entity.User;
import com.example.bank.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<UserResponse> get(Authentication authentication) {
        return ResponseEntity.ok(profileService.get(principal(authentication)));
    }

    @PutMapping
    public ResponseEntity<UserResponse> update(
            @Valid @RequestBody ProfileDto.UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(profileService.update(principal(authentication), request));
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ProfileDto.PasswordChangeRequest request,
            Authentication authentication
    ) {
        profileService.changePassword(principal(authentication), request);
        return ResponseEntity.ok(Map.of("message", "Password changed"));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
