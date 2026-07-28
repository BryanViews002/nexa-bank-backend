package com.example.bank.controller;

import com.example.bank.dto.AccountDto;
import com.example.bank.dto.AccountControlDto;
import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.BankMapper;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.entity.Account;
import com.example.bank.entity.User;
import com.example.bank.service.AccountService;
import com.example.bank.service.KycGuardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping({"/accounts", "/api/v1/accounts"})
public class AccountController {

    private final AccountService accountService;
    private final KycGuardService kycGuardService;
    private final BankMapper bankMapper;

    public AccountController(
            AccountService accountService,
            KycGuardService kycGuardService,
            BankMapper bankMapper
    ) {
        this.accountService = accountService;
        this.kycGuardService = kycGuardService;
        this.bankMapper = bankMapper;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(
            @Valid @RequestBody AccountDto request,
            Authentication authentication
    ) {
        User user = principal(authentication);
        kycGuardService.requireApproved(user);
        Account account = accountService.openAccount(
                user,
                request.getType(),
                BigDecimal.ZERO,
                request.getCurrency(),
                request.getDisplayName()
        );
        return ResponseEntity.ok(bankMapper.toAccountResponse(account));
    }

    @PostMapping("/open")
    public ResponseEntity<AccountResponse> openAccountLegacy(
            @Valid @RequestBody AccountDto request,
            Authentication authentication
    ) {
        return openAccount(request, authentication);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts(Authentication authentication) {
        List<AccountResponse> accounts = accountService.getUserAccounts(principal(authentication)).stream()
                .map(bankMapper::toAccountResponse)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                bankMapper.toAccountResponse(accountService.getAccount(id, principal(authentication)))
        );
    }

    @GetMapping("/{id}/mini-statement")
    public ResponseEntity<List<TransactionResponse>> miniStatement(
            @PathVariable Long id,
            Authentication authentication
    ) {
        List<TransactionResponse> transactions = accountService
                .getMiniStatement(id, principal(authentication))
                .stream()
                .map(bankMapper::toTransactionResponse)
                .toList();
        return ResponseEntity.ok(transactions);
    }

    @PatchMapping("/{id}/controls")
    public ResponseEntity<AccountResponse> updateControls(
            @PathVariable Long id,
            @Valid @RequestBody AccountControlDto.UpdateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                bankMapper.toAccountResponse(
                        accountService.updateControls(id, request, principal(authentication))
                )
        );
    }

    @PostMapping("/{id}/freeze")
    public ResponseEntity<AccountResponse> freeze(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                bankMapper.toAccountResponse(accountService.freeze(id, principal(authentication)))
        );
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<AccountResponse> unfreeze(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(
                bankMapper.toAccountResponse(accountService.unfreeze(id, principal(authentication)))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> close(@PathVariable Long id, Authentication authentication) {
        accountService.close(id, principal(authentication));
        return ResponseEntity.noContent().build();
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
