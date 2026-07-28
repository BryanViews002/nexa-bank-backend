package com.example.bank.controller;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.KycDocumentResponse;
import com.example.bank.dto.UserResponse;
import com.example.bank.entity.AuditLog;
import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.service.AdminService;
import com.example.bank.service.AuditService;
import com.example.bank.service.KycService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/admin", "/api/v1/admin"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final KycService kycService;
    private final AuditService auditService;
    private final BankMapper bankMapper;

    public AdminController(
            AdminService adminService,
            KycService kycService,
            AuditService auditService,
            BankMapper bankMapper
    ) {
        this.adminService = adminService;
        this.kycService = kycService;
        this.auditService = auditService;
        this.bankMapper = bankMapper;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(
                adminService.getAllUsers().stream().map(bankMapper::toUserResponse).toList()
        );
    }

    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable Long id) {
        adminService.unlockUser(id);
        return ResponseEntity.ok(Map.of("message", "User unlocked"));
    }

    @GetMapping("/kyc")
    public ResponseEntity<List<KycDocumentResponse>> getPendingKyc() {
        return ResponseEntity.ok(
                kycService.getPendingKyc().stream()
                        .map(bankMapper::toKycDocumentResponse)
                        .toList()
        );
    }

    @PostMapping("/kyc/{id}/approve")
    public ResponseEntity<KycDocumentResponse> approveKyc(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                bankMapper.toKycDocumentResponse(
                        kycService.approve(id, (User) authentication.getPrincipal())
                )
        );
    }

    @PostMapping("/kyc/{id}/reject")
    public ResponseEntity<KycDocumentResponse> rejectKyc(
            @PathVariable Long id,
            @Valid @RequestBody RejectionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                bankMapper.toKycDocumentResponse(
                        kycService.reject(id, request.reason(), (User) authentication.getPrincipal())
                )
        );
    }

    @GetMapping("/kyc/{id}/document")
    public ResponseEntity<Resource> downloadKycDocument(@PathVariable Long id) throws IOException {
        KycDocument document = kycService.getDocument(id);
        Resource resource = kycService.loadDocument(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFilename()).build().toString()
                )
                .body(resource);
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogs(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(auditService.getLogs(params));
    }

    public record RejectionRequest(@NotBlank String reason) {
    }
}
