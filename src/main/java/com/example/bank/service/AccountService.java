package com.example.bank.service;

import com.example.bank.entity.Account;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.util.AccountNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Account openAccount(User user, String typeStr, double initialBalance) {
        log.info("Opening account for user ID: {}, type: {}, initialBalance: {}", user.getId(), typeStr, initialBalance);
        Account.AccountType type;
        try {
            type = Account.AccountType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid account type: {}", typeStr);
            throw new IllegalArgumentException("Invalid account type: " + typeStr);
        }
        Account account = new Account();
        account.setAccountNumber(AccountNumberGenerator.generate());
        account.setUser(user);
        account.setType(type);
        account.setBalance(initialBalance);
        Account savedAccount = accountRepository.save(account);
        log.info("Account created: ID={}, accountNumber={}, balance={}",
                savedAccount.getId(), savedAccount.getAccountNumber(), savedAccount.getBalance());
        return savedAccount;
    }

    public List<Account> getUserAccounts(User user) {
        log.debug("Fetching accounts for user ID: {}", user.getId());
        List<Account> accounts = accountRepository.findAll().stream()
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());
        log.debug("Found {} accounts for user ID: {}", accounts.size(), user.getId());
        return accounts;
    }

    public Account getAccount(Long id, User user) {
        log.debug("Fetching account ID: {} for user ID: {}", id, user.getId());
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account not found: ID={}", id);
                    return new IllegalArgumentException("Account not found");
                });
        if (!account.getUser().getId().equals(user.getId())) {
            log.warn("Unauthorized access to account ID: {} by user ID: {}", id, user.getId());
            throw new AccessDeniedException("Not owner");
        }
        return account;
    }

    public List<Transaction> getMiniStatement(Long id, User user) {
        log.debug("Fetching mini-statement for account ID: {} by user ID: {}", id, user.getId());
        Account account = getAccount(id, user);
        List<Transaction> history = transactionRepository.findByFromAccountOrToAccount(account, account);
        List<Transaction> limitedHistory = history.stream().limit(5).collect(Collectors.toList());
        log.debug("Retrieved {} transactions for account ID: {}", limitedHistory.size(), id);
        return limitedHistory;
    }
}