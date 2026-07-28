package com.example.bank.controller;

import com.example.bank.dto.ExternalTransferDto;
import com.example.bank.service.PaymentRailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated provider callback endpoint. Trust comes solely from the HMAC signature
 * over the raw request body, so the body is taken as a string and only parsed once the
 * signature has been verified.
 *
 * <p>Deliberately mapped only under {@code /api/v1} to match the exact path allow-listed
 * in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/payment-rails/webhooks")
public class PaymentRailWebhookController {

    private final PaymentRailService paymentRailService;
    private final ObjectMapper objectMapper;

    public PaymentRailWebhookController(PaymentRailService paymentRailService, ObjectMapper objectMapper) {
        this.paymentRailService = paymentRailService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> receive(
            @PathVariable String provider,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload
    ) {
        if (!paymentRailService.verifySignature(provider, rawPayload, signature)) {
            throw new AccessDeniedException("Invalid webhook signature");
        }
        ExternalTransferDto.WebhookEvent event;
        try {
            event = objectMapper.readValue(rawPayload, ExternalTransferDto.WebhookEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Malformed webhook payload");
        }
        paymentRailService.handleWebhook(provider, event);
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}
