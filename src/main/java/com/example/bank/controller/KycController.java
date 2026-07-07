// src/main/java/com/example/bank/controller/KycController.java
package com.example.bank.controller;

import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.service.KycService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/kyc")
public class KycController {
    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadKyc(@RequestPart("file") MultipartFile file, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        try {
            KycDocument doc = kycService.upload(file, user);
            return ResponseEntity.ok(doc);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }
}