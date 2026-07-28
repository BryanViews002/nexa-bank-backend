package com.example.bank.controller;

import com.example.bank.dto.NotificationPreferenceDto;
import com.example.bank.dto.NotificationResponse;
import com.example.bank.entity.User;
import com.example.bank.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.list(principal(authentication), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(principal(authentication))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.markRead(id, principal(authentication)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication authentication) {
        notificationService.markAllRead(principal(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDto> preferences(Authentication authentication) {
        return ResponseEntity.ok(notificationService.getPreferences(principal(authentication)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferenceDto> updatePreferences(
            @RequestBody NotificationPreferenceDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(notificationService.updatePreferences(principal(authentication), request));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
