package com.example.bank.controller;

import com.example.bank.dto.BudgetDto;
import com.example.bank.entity.User;
import com.example.bank.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/budgets", "/api/v1/budgets"})
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    public ResponseEntity<BudgetDto.Response> upsert(
            @Valid @RequestBody BudgetDto.UpsertRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(budgetService.upsert(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<BudgetDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(budgetService.list(principal(authentication)));
    }

    @GetMapping("/summary")
    public ResponseEntity<BudgetDto.Summary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            Authentication authentication
    ) {
        return ResponseEntity.ok(budgetService.summary(principal(authentication), month));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BudgetDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody BudgetDto.UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(budgetService.update(id, request, principal(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        budgetService.delete(id, principal(authentication));
        return ResponseEntity.noContent().build();
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
