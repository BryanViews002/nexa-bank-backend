package com.example.bank.controller;

import com.example.bank.dto.TransactionResponse;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.service.StatementService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> statement(
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
        return ResponseEntity.ok(statementService.statement(
                accountId,
                (User) authentication.getPrincipal(),
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
        ));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @PathVariable Long accountId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication
    ) {
        byte[] content = statementService.csv(accountId, (User) authentication.getPrincipal(), startDate, endDate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("nexa-statement-" + accountId + ".csv").build().toString()
                )
                .body(content);
    }
}
