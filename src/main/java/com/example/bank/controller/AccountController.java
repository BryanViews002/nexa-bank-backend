package com.example.bank.controller;

import com.example.bank.dto.AccountDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.User;
import com.example.bank.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/open")
    public ResponseEntity<Account> openAccount(@RequestBody AccountDto request, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        // Apply $25 bonus for the first account
        boolean isFirstAccount = accountService.getUserAccounts(user).isEmpty();
        Account account = accountService.openAccount(user, request.getType(), isFirstAccount ? 25.0 : 0.0);
        return ResponseEntity.ok(account);
    }

    @GetMapping
    public ResponseEntity<List<Account>> listAccounts(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.getUserAccounts(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.getAccount(id, user));
    }

    @GetMapping("/{id}/mini-statement")
    public ResponseEntity<List<com.example.bank.entity.Transaction>> miniStatement(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.getMiniStatement(id, user));
    }
}