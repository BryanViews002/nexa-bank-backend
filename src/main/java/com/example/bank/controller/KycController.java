package com.example.bank.controller;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.KycDocumentResponse;
import com.example.bank.dto.KycState;
import com.example.bank.dto.KycStatusResponse;
import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.service.KycGuardService;
import com.example.bank.service.KycService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping({"/kyc", "/api/v1/kyc"})
public class KycController {

    private final KycService kycService;
    private final KycGuardService kycGuardService;
    private final BankMapper bankMapper;

    public KycController(
            KycService kycService,
            KycGuardService kycGuardService,
            BankMapper bankMapper
    ) {
        this.kycService = kycService;
        this.kycGuardService = kycGuardService;
        this.bankMapper = bankMapper;
    }

    @PostMapping(
            value = {"", "/documents"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<KycDocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        KycDocument document = kycService.upload(file, principal(authentication));
        return ResponseEntity.ok(bankMapper.toKycDocumentResponse(document));
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<KycDocumentResponse> uploadLegacy(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) throws IOException {
        return upload(file, authentication);
    }

    @GetMapping
    public ResponseEntity<KycStatusResponse> status(Authentication authentication) {
        User user = principal(authentication);
        List<KycDocumentResponse> documents = kycService.getUserDocuments(user).stream()
                .map(bankMapper::toKycDocumentResponse)
                .toList();
        KycState state = KycState.from(kycGuardService.getCurrentStatus(user));
        return ResponseEntity.ok(new KycStatusResponse(state, documents));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
