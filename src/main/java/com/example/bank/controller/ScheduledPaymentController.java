package com.example.bank.controller;

import com.example.bank.dto.ScheduledPaymentDto;
import com.example.bank.entity.User;
import com.example.bank.service.ScheduledPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scheduled-payments")
public class ScheduledPaymentController {

    private final ScheduledPaymentService scheduledPaymentService;

    public ScheduledPaymentController(ScheduledPaymentService scheduledPaymentService) {
        this.scheduledPaymentService = scheduledPaymentService;
    }

    @PostMapping
    public ResponseEntity<ScheduledPaymentDto.Response> create(
            @Valid @RequestBody ScheduledPaymentDto.CreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduledPaymentService.create(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledPaymentDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(scheduledPaymentService.list(principal(authentication)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScheduledPaymentDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody ScheduledPaymentDto.UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduledPaymentService.update(id, request, principal(authentication)));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<ScheduledPaymentDto.Response> runNow(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(scheduledPaymentService.runNow(id, principal(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        scheduledPaymentService.delete(id, principal(authentication));
        return ResponseEntity.noContent().build();
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
