package com.example.bank.controller;

import com.example.bank.dto.ExternalTransferDto;
import com.example.bank.entity.User;
import com.example.bank.service.PaymentRailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/payment-rails", "/api/v1/payment-rails"})
public class PaymentRailController {

    private final PaymentRailService paymentRailService;

    public PaymentRailController(PaymentRailService paymentRailService) {
        this.paymentRailService = paymentRailService;
    }

    @PostMapping("/funding")
    public ResponseEntity<ExternalTransferDto.Response> fund(
            @Valid @RequestBody ExternalTransferDto.InitiateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRailService.initiateFunding(request, principal(authentication)));
    }

    @PostMapping("/payouts")
    public ResponseEntity<ExternalTransferDto.Response> payout(
            @Valid @RequestBody ExternalTransferDto.InitiateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                paymentRailService.initiatePayout(request, principal(authentication), idempotencyKey)
        );
    }

    @GetMapping("/transfers")
    public ResponseEntity<List<ExternalTransferDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(paymentRailService.list(principal(authentication)));
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<ExternalTransferDto.Response> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRailService.get(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
