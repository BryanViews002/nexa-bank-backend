package com.example.bank.controller;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.UserSearchDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping({"/users", "/api/v1/users"})
public class UserSearchController {

    private final AccountRepository accountRepository;
    private final BankMapper bankMapper;

    public UserSearchController(AccountRepository accountRepository, BankMapper bankMapper) {
        this.accountRepository = accountRepository;
        this.bankMapper = bankMapper;
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchDto>> searchUsers(
            @RequestParam String query,
            Authentication authentication
    ) {
        if (query == null || query.trim().length() < 2) {
            throw new IllegalArgumentException("Search query must contain at least two characters");
        }
        User currentUser = (User) authentication.getPrincipal();
        LinkedHashMap<Long, UserSearchDto> uniqueUsers = new LinkedHashMap<>();
        for (Account account : accountRepository.searchRecipientAccounts(
                currentUser.getId(),
                query.trim(),
                PageRequest.of(0, 20)
        )) {
            uniqueUsers.putIfAbsent(
                    account.getUser().getId(),
                    new UserSearchDto(
                            account.getUser().getId(),
                            account.getUser().getUsername(),
                            account.getUser().getFullName(),
                            bankMapper.maskAccountNumber(account.getAccountNumber())
                    )
            );
        }
        return ResponseEntity.ok(uniqueUsers.values().stream().limit(10).toList());
    }
}
