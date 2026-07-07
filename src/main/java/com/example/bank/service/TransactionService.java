package com.example.bank.service;

import com.example.bank.dto.TransferDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.ScheduledPayment;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.exception.InsufficientFundsException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@Slf4j
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                              UserRepository userRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public List<Transaction> getAllUserTransactions(User user) {
        log.debug("Fetching all transactions for user: {}", user.getUsername());
        List<Account> userAccounts = accountRepository.findAllByUserId(user.getId());

        if (userAccounts.isEmpty()) {
            log.debug("No accounts found for user: {}", user.getUsername());
            return List.of();
        }

        List<Transaction> transactions = transactionRepository.findByFromAccountInOrToAccountIn(userAccounts, userAccounts);
        log.debug("Found {} transactions for user: {}", transactions.size(), user.getUsername());
        return transactions;
    }

    @Transactional
    public Transaction deposit(Long accountId, double amount, User user) {
        log.debug("Deposit attempt: {} to account ID: {} by user: {}", amount, accountId, user.getUsername());
        Account to = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (!to.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to deposit to this account");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Transaction tx = new Transaction();
        tx.setToAccount(to);
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.DEPOSIT);
        tx.setStatus(Transaction.TransactionStatus.PENDING);
        transactionRepository.save(tx);

        to.setBalance(to.getBalance() + amount);
        accountRepository.save(to);

        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        auditService.log(user.getId(), "DEPOSIT_COMPLETED", Map.of("txId", tx.getId(), "amount", amount));
        log.info("Deposit completed: {} to account ID: {} by user: {}", amount, accountId, user.getUsername());
        return tx;
    }

    @Transactional
    public Transaction withdraw(Long accountId, double amount, User user) {
        log.debug("Withdraw attempt: {} from account ID: {} by user: {}", amount, accountId, user.getUsername());
        Account from = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (!from.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to withdraw from this account");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (from.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds for withdrawal. Available: " + from.getBalance() + ", Requested: " + amount);
        }

        Transaction tx = new Transaction();
        tx.setFromAccount(from);
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.WITHDRAW);
        tx.setStatus(Transaction.TransactionStatus.PENDING);
        transactionRepository.save(tx);

        from.setBalance(from.getBalance() - amount);
        accountRepository.save(from);

        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        auditService.log(user.getId(), "WITHDRAW_COMPLETED", Map.of("txId", tx.getId(), "amount", amount));
        log.info("Withdrawal completed: {} from account ID: {} by user: {}", amount, accountId, user.getUsername());
        return tx;
    }

    @Transactional
    public Transaction transfer(Long fromAccountId, String toIdentifier, double amount, User user) {
        log.info("Transfer request: fromAccountId={}, toIdentifier={}, amount={}",
                fromAccountId, toIdentifier, amount);

        Account fromAccount = accountRepository.findByIdForUpdate(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to transfer from this account");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        if (fromAccount.getBalance() < amount) {
            throw new InsufficientFundsException("Insufficient funds for transfer. Available: " + fromAccount.getBalance() + ", Requested: " + amount);
        }

        // Find destination account by account number
        Account toAccount = accountRepository.findByAccountNumber(toIdentifier)
                .orElseThrow(() -> new IllegalArgumentException("Recipient account not found"));

        Transaction tx = new Transaction();
        tx.setFromAccount(fromAccount);
        tx.setToAccount(toAccount);
        tx.setAmount(amount);
        tx.setType(Transaction.TransactionType.TRANSFER);
        tx.setStatus(Transaction.TransactionStatus.PENDING);
        transactionRepository.save(tx);

        fromAccount.setBalance(fromAccount.getBalance() - amount);
        toAccount.setBalance(toAccount.getBalance() + amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(tx);
        auditService.log(user.getId(), "TRANSFER_COMPLETED",
                Map.of("txId", tx.getId(), "amount", amount, "toIdentifier", toIdentifier));
        log.info("Transfer completed: {} from account ID: {} to account number: {} by user: {}",
                amount, fromAccountId, toIdentifier, user.getUsername());
        return tx;
    }

    public List<Transaction> getHistory(Long accountId, User user, Map<String, String> params) {
        log.debug("Fetching transaction history for account ID: {} by user: {}", accountId, user.getUsername());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to view this account's history");
        }
        return transactionRepository.findByFromAccountOrToAccount(account, account);
    }

    @Transactional
    public Transaction transferInternal(ScheduledPayment sp) {
        log.debug("Internal transfer for scheduled payment: {} to {}", sp.getAmount(), sp.getAccountTo());
        TransferDto dto = new TransferDto();
        dto.setFromAccountId(sp.getAccountFrom().getId());
        dto.setToIdentifier(sp.getAccountTo());
        dto.setAmount(sp.getAmount().doubleValue());
        return transfer(dto.getFromAccountId(), dto.getToIdentifier(), dto.getAmount(), sp.getAccountFrom().getUser());
    }
}