package com.example.bank.controller;

import com.example.bank.dto.TransferDto;
import com.example.bank.dto.UserSearchDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.service.TransactionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/transactions")
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<?> getAllUserTransactions(Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            List<Transaction> transactions = transactionService.getAllUserTransactions(user);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Failed to fetch transactions for user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "ERROR", "message", "Failed to retrieve transactions: " + e.getMessage()));
        }
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            Object accountIdObj = body.get("toAccountId");
            if (accountIdObj == null) {
                accountIdObj = body.get("accountId");
            }
            if (accountIdObj == null) {
                throw new IllegalArgumentException("Missing required parameter: accountId or toAccountId");
            }
            Object amountObj = body.get("amount");
            if (amountObj == null) {
                throw new IllegalArgumentException("Missing required parameter: amount");
            }
            Long accountId = Long.parseLong(accountIdObj.toString());
            double amount = Double.parseDouble(amountObj.toString());
            Transaction tx = transactionService.deposit(accountId, amount, user);
            return ResponseEntity.ok(Map.of(
                    "message", "Deposit successful to Nexa account",
                    "transactionId", tx.getId(),
                    "amount", amount
            ));
        } catch (NumberFormatException e) {
            log.error("Invalid number format in deposit request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", "Invalid amount or account ID format"));
        } catch (Exception e) {
            log.error("Deposit failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            Object accountIdObj = body.get("fromAccountId");
            if (accountIdObj == null) {
                accountIdObj = body.get("accountId");
            }
            if (accountIdObj == null) {
                throw new IllegalArgumentException("Missing required parameter: accountId or fromAccountId");
            }
            Object amountObj = body.get("amount");
            if (amountObj == null) {
                throw new IllegalArgumentException("Missing required parameter: amount");
            }
            Long accountId = Long.parseLong(accountIdObj.toString());
            double amount = Double.parseDouble(amountObj.toString());
            Transaction tx = transactionService.withdraw(accountId, amount, user);
            return ResponseEntity.ok(Map.of(
                    "message", "Withdrawal successful from Nexa account",
                    "transactionId", tx.getId(),
                    "amount", amount
            ));
        } catch (NumberFormatException e) {
            log.error("Invalid number format in withdraw request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", "Invalid amount or account ID format"));
        } catch (Exception e) {
            log.error("Withdrawal failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", e.getMessage()));
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@Valid @RequestBody TransferDto dto, Authentication authentication) {
        User user = null;
        try {
            user = (User) authentication.getPrincipal();
            Transaction tx = transactionService.transfer(dto.getFromAccountId(), dto.getToIdentifier(), dto.getAmount(), user);
            return ResponseEntity.ok(Map.of(
                    "message", "Transfer successful to Nexa user",
                    "transactionId", tx.getId(),
                    "amount", dto.getAmount(),
                    "toIdentifier", dto.getToIdentifier()
            ));
        } catch (IllegalArgumentException e) {
            log.error("Transfer failed for user {}: Invalid input - {}", user != null ? user.getUsername() : "unknown", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", "Invalid transfer details: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Transfer failed for user {}: {}", user != null ? user.getUsername() : "unknown", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", e.getMessage()));
        }
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> history(@PathVariable Long accountId, @RequestParam Map<String, String> params, Authentication authentication) {
        try {
            User user = (User) authentication.getPrincipal();
            List<Transaction> history = transactionService.getHistory(accountId, user, params);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException e) {
            log.error("History fetch failed for account {}: Invalid input - {}", accountId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "ERROR", "message", "Invalid account ID: " + e.getMessage()));
        } catch (Exception e) {
            log.error("History fetch failed for account {}: {}", accountId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "ERROR", "message", "Failed to retrieve transaction history: " + e.getMessage()));
        }
    }
}

// Added UserController as a nested class for clarity (can be separated into its own file)
@RestController
@RequestMapping("/users")
class UserController {

    private final AccountRepository accountRepository;

    public UserController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchDto>> searchUsers(
            @RequestParam String query,
            Authentication authentication) {

        User currentUser = (User) authentication.getPrincipal();

        List<UserSearchDto> results = accountRepository.findAll().stream()
                .filter(account -> !account.getUser().getId().equals(currentUser.getId())) // Exclude self
                .filter(account ->
                        account.getAccountNumber().contains(query) ||
                                (account.getUser().getUsername() != null &&
                                        account.getUser().getUsername().toLowerCase().contains(query.toLowerCase())) ||
                                account.getUser().getFullName().toLowerCase().contains(query.toLowerCase())
                )
                .map(account -> new UserSearchDto(
                        account.getUser().getId(),
                        account.getUser().getUsername(),
                        account.getUser().getFullName(),
                        account.getAccountNumber()
                ))
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }
}