package com.example.bank.controller;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.CashOperationRequest;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.dto.TransferDto;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/transactions", "/api/v1/transactions"})
public class TransactionController {

    private final TransactionService transactionService;
    private final BankMapper bankMapper;

    public TransactionController(TransactionService transactionService, BankMapper bankMapper) {
        this.transactionService = transactionService;
        this.bankMapper = bankMapper;
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getRecentTransactions(Authentication authentication) {
        List<TransactionResponse> transactions = transactionService
                .getAllUserTransactions(principal(authentication))
                .stream()
                .map(bankMapper::toTransactionResponse)
                .toList();
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody CashOperationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        Transaction transaction = transactionService.deposit(
                request.accountId(),
                request.amount(),
                principal(authentication),
                idempotencyKey,
                request.description(),
                request.category()
        );
        return ResponseEntity.ok(bankMapper.toTransactionResponse(transaction));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody CashOperationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        Transaction transaction = transactionService.withdraw(
                request.accountId(),
                request.amount(),
                principal(authentication),
                idempotencyKey,
                request.description(),
                request.category()
        );
        return ResponseEntity.ok(bankMapper.toTransactionResponse(transaction));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        Transaction transaction = transactionService.transfer(
                request.getFromAccountId(),
                request.getToIdentifier(),
                request.getAmount(),
                principal(authentication),
                idempotencyKey,
                request.getDescription(),
                request.getCategory()
        );
        return ResponseEntity.ok(bankMapper.toTransactionResponse(transaction));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<TransactionResponse>> history(
            @PathVariable Long accountId,
            @RequestParam(required = false) Transaction.TransactionType type,
            @RequestParam(required = false) Transaction.TransactionStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication
    ) {
        List<TransactionResponse> transactions = transactionService.getHistory(
                        accountId,
                        principal(authentication),
                        type,
                        status,
                        category,
                        startDate,
                        endDate,
                        minAmount,
                        maxAmount,
                        query,
                        page,
                        size
                )
                .map(bankMapper::toTransactionResponse)
                .getContent();
        return ResponseEntity.ok(transactions);
    }

    @PatchMapping("/{transactionId}/category")
    public ResponseEntity<TransactionResponse> updateCategory(
            @PathVariable Long transactionId,
            @Valid @RequestBody CategoryRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                bankMapper.toTransactionResponse(
                        transactionService.updateCategory(
                                transactionId,
                                request.category(),
                                principal(authentication)
                        )
                )
        );
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    public record CategoryRequest(@NotBlank String category) {
    }
}
