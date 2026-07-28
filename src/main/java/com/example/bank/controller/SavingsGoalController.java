package com.example.bank.controller;

import com.example.bank.dto.SavingsGoalDto;
import com.example.bank.entity.User;
import com.example.bank.service.SavingsGoalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/savings-goals", "/api/v1/savings-goals"})
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;

    public SavingsGoalController(SavingsGoalService savingsGoalService) {
        this.savingsGoalService = savingsGoalService;
    }

    @PostMapping
    public ResponseEntity<SavingsGoalDto.Response> create(
            @Valid @RequestBody SavingsGoalDto.CreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savingsGoalService.create(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<SavingsGoalDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(savingsGoalService.list(principal(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavingsGoalDto.Response> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savingsGoalService.get(id, principal(authentication)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SavingsGoalDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody SavingsGoalDto.UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savingsGoalService.update(id, request, principal(authentication)));
    }

    @PostMapping("/{id}/contribute")
    public ResponseEntity<SavingsGoalDto.Response> contribute(
            @PathVariable Long id,
            @Valid @RequestBody SavingsGoalDto.AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                savingsGoalService.contribute(id, request.amount(), principal(authentication), idempotencyKey)
        );
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SavingsGoalDto.Response> withdraw(
            @PathVariable Long id,
            @Valid @RequestBody SavingsGoalDto.AmountRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                savingsGoalService.withdraw(id, request.amount(), principal(authentication), idempotencyKey)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<SavingsGoalDto.Response> cancel(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(savingsGoalService.cancel(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
