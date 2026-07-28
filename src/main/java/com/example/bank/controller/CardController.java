package com.example.bank.controller;

import com.example.bank.dto.CardDto;
import com.example.bank.entity.User;
import com.example.bank.service.CardService;
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
@RequestMapping({"/cards", "/api/v1/cards"})
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ResponseEntity<CardDto.IssuedResponse> issue(
            @Valid @RequestBody CardDto.IssueRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.issue(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<CardDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(cardService.list(principal(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CardDto.Response> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cardService.get(id, principal(authentication)));
    }

    @PatchMapping("/{id}/controls")
    public ResponseEntity<CardDto.Response> updateControls(
            @PathVariable Long id,
            @Valid @RequestBody CardDto.ControlRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.updateControls(id, request, principal(authentication)));
    }

    @PostMapping("/{id}/freeze")
    public ResponseEntity<CardDto.Response> freeze(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cardService.freeze(id, principal(authentication)));
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<CardDto.Response> unfreeze(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cardService.unfreeze(id, principal(authentication)));
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<CardDto.Response> purchase(
            @PathVariable Long id,
            @Valid @RequestBody CardDto.PurchaseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                cardService.purchase(id, request, principal(authentication), idempotencyKey)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CardDto.Response> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cardService.cancel(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
