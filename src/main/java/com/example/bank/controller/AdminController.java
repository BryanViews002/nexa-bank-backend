// src/main/java/com/example/bank/controller/AdminController.java
package com.example.bank.controller;

import com.example.bank.entity.AuditLog;
import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.service.AdminService;
import com.example.bank.service.AuditService;
import com.example.bank.service.KycService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final KycService kycService;
    private final AuditService auditService;

    public AdminController(AdminService adminService, KycService kycService, AuditService auditService) {
        this.adminService = adminService;
        this.kycService = kycService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable Long id) {
        adminService.unlockUser(id);
        return ResponseEntity.ok("Unlocked");
    }

    @GetMapping("/kyc")
    public ResponseEntity<List<KycDocument>> getPendingKyc() {
        return ResponseEntity.ok(kycService.getPendingKyc());
    }

    @PostMapping("/kyc/{id}/approve")
    public ResponseEntity<?> approveKyc(@PathVariable Long id) {
        kycService.approve(id);
        return ResponseEntity.ok("Approved");
    }

    @PostMapping("/kyc/{id}/reject")
    public ResponseEntity<?> rejectKyc(@PathVariable Long id) {
        kycService.reject(id);
        return ResponseEntity.ok("Rejected");
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getAuditLogs(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(auditService.getLogs(params));
    }
}