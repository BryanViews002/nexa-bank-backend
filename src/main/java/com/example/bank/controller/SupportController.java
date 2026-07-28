package com.example.bank.controller;

import com.example.bank.dto.SupportDto;
import com.example.bank.entity.User;
import com.example.bank.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/support/tickets", "/api/v1/support/tickets"})
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping
    public ResponseEntity<SupportDto.TicketResponse> create(
            @Valid @RequestBody SupportDto.CreateTicketRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(supportService.create(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<Page<SupportDto.TicketResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(supportService.listMine(principal(authentication), page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportDto.TicketResponse> get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(supportService.get(id, principal(authentication)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportDto.TicketResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody SupportDto.MessageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(supportService.reply(id, request, principal(authentication)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<SupportDto.TicketResponse> close(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(supportService.close(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
