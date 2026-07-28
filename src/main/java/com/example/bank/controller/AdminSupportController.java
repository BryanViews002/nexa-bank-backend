package com.example.bank.controller;

import com.example.bank.dto.SupportDto;
import com.example.bank.entity.User;
import com.example.bank.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/admin/support/tickets", "/api/v1/admin/support/tickets"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminSupportController {

    private final SupportService supportService;

    public AdminSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public ResponseEntity<Page<SupportDto.TicketResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(supportService.listAllForAdmin(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportDto.TicketResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(supportService.getForAdmin(id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportDto.TicketResponse> reply(
            @PathVariable Long id,
            @Valid @RequestBody SupportDto.MessageRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                supportService.replyAsAdmin(id, request, (User) authentication.getPrincipal())
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SupportDto.TicketResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SupportDto.AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(supportService.updateAsAdmin(id, request));
    }
}
