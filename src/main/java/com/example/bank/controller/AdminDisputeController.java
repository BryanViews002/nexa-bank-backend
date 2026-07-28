package com.example.bank.controller;

import com.example.bank.dto.DisputeDto;
import com.example.bank.entity.User;
import com.example.bank.service.DisputeService;
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
@RequestMapping({"/admin/disputes", "/api/v1/admin/disputes"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {

    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public ResponseEntity<Page<DisputeDto.Response>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(disputeService.listForAdmin(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeDto.Response> get(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getForAdmin(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DisputeDto.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody DisputeDto.AdminUpdateRequest request
    ) {
        return ResponseEntity.ok(disputeService.updateAsAdmin(id, request));
    }

    @PostMapping("/{id}/provisional-credit")
    public ResponseEntity<DisputeDto.Response> provisionalCredit(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                disputeService.grantProvisionalCredit(id, (User) authentication.getPrincipal())
        );
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<DisputeDto.Response> resolve(
            @PathVariable Long id,
            @Valid @RequestBody DisputeDto.ResolveRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                disputeService.resolve(id, request, (User) authentication.getPrincipal())
        );
    }
}
