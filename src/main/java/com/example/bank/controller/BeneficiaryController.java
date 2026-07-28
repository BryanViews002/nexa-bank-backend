package com.example.bank.controller;

import com.example.bank.dto.BeneficiaryDto;
import com.example.bank.entity.User;
import com.example.bank.service.BeneficiaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @PostMapping
    public ResponseEntity<BeneficiaryDto.Response> create(
            @Valid @RequestBody BeneficiaryDto.CreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(beneficiaryService.create(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.list(principal(authentication)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BeneficiaryDto.Response> updateNickname(
            @PathVariable Long id,
            @Valid @RequestBody NicknameRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                beneficiaryService.updateNickname(id, request.nickname(), principal(authentication))
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication authentication) {
        beneficiaryService.remove(id, principal(authentication));
        return ResponseEntity.noContent().build();
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }

    public record NicknameRequest(@Size(max = 80) String nickname) {
    }
}
