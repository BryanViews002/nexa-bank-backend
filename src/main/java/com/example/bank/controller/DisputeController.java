package com.example.bank.controller;

import com.example.bank.dto.DisputeDto;
import com.example.bank.entity.User;
import com.example.bank.service.DisputeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/disputes", "/api/v1/disputes"})
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<DisputeDto.Response> file(
            @Valid @RequestBody DisputeDto.CreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(disputeService.file(request, principal(authentication)));
    }

    @GetMapping
    public ResponseEntity<List<DisputeDto.Response>> list(Authentication authentication) {
        return ResponseEntity.ok(disputeService.listMine(principal(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeDto.Response> get(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(disputeService.get(id, principal(authentication)));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<DisputeDto.Response> withdraw(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return ResponseEntity.ok(disputeService.withdraw(id, principal(authentication)));
    }

    private User principal(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
