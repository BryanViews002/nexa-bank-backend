package com.example.bank.controller;

import com.example.bank.dto.PaymentRequestDto;
import com.example.bank.entity.User;
import com.example.bank.service.PaymentRequestService;
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
@RequestMapping({"/payment-requests", "/api/v1/payment-requests"})
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @PostMapping
    public ResponseEntity<PaymentRequestDto.Response> create(
            @Valid @RequestBody PaymentRequestDto.CreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRequestService.create(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<PaymentRequestDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(paymentRequestService.listAll(principal(authentication)));
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<PaymentRequestDto.Response>> incoming(Authentication authentication) {
        return ResponseEntity.ok(paymentRequestService.listIncoming(principal(authentication)));
    }

    @GetMapping("/outgoing")
    public ResponseEntity<List<PaymentRequestDto.Response>> outgoing(Authentication authentication) {
        return ResponseEntity.ok(paymentRequestService.listOutgoing(principal(authentication)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<PaymentRequestDto.Response> accept(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequestDto.AcceptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRequestService.accept(
                id,
                request.payerAccountId(),
                principal(authentication),
                idempotencyKey
        ));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<PaymentRequestDto.Response> decline(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRequestService.decline(id, principal(authentication)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentRequestDto.Response> cancel(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(paymentRequestService.cancel(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
