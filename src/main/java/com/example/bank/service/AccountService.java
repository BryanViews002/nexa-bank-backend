package com.example.bank.service;

import com.example.bank.entity.Account;
import com.example.bank.entity.LedgerAccount;
import com.example.bank.entity.LedgerPosting;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.dto.AccountControlDto;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.util.AccountNumberGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;

    public AccountService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            LedgerService ledgerService
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Account openAccount(
            User user,
            String typeValue,
            BigDecimal initialBalance,
            String currency,
            String displayName
    ) {
        Account.AccountType type;
        try {
            type = Account.AccountType.valueOf(typeValue.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid account type: " + typeValue);
        }

        String normalizedCurrency = currency == null ? "USD" : currency.toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        }

        Account account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setUser(user);
        account.setType(type);
        account.setCurrency(normalizedCurrency);
        account.setDisplayName(
                displayName == null || displayName.isBlank()
                        ? type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT)
                        : displayName.trim()
        );
        account.setBalance(BigDecimal.ZERO);
        account = accountRepository.save(account);
        LedgerAccount customerLedger = ledgerService.ensureCustomerAccount(account);

        if (initialBalance != null && initialBalance.signum() > 0) {
            Transaction bonus = new Transaction();
            bonus.setToAccount(account);
            bonus.setAmount(initialBalance);
            bonus.setCurrency(normalizedCurrency);
            bonus.setType(Transaction.TransactionType.BONUS);
            bonus.setStatus(Transaction.TransactionStatus.COMPLETED);
            bonus.setDescription("Account opening bonus");
            bonus.setCategory("BONUS");
            bonus.setInitiatedByUserId(user.getId());
            bonus = transactionRepository.save(bonus);

            LedgerAccount bonusExpense = ledgerService.systemAccount(
                    "BONUS_EXPENSE",
                    normalizedCurrency,
                    LedgerAccount.LedgerAccountType.EXPENSE
            );
            ledgerService.post(
                    bonus,
                    "ACCOUNT_OPENING_BONUS",
                    "Account opening bonus",
                    List.of(
                            new LedgerService.PostingRequest(
                                    bonusExpense,
                                    LedgerPosting.PostingDirection.DEBIT,
                                    initialBalance,
                                    normalizedCurrency
                            ),
                            new LedgerService.PostingRequest(
                                    customerLedger,
                                    LedgerPosting.PostingDirection.CREDIT,
                                    initialBalance,
                                    normalizedCurrency
                            )
                    )
            );
            account.setBalance(initialBalance);
            account = accountRepository.save(account);
        }
        return account;
    }

    public Account openAccount(User user, String type, BigDecimal initialBalance) {
        return openAccount(user, type, initialBalance, "USD", null);
    }

    public List<Account> getUserAccounts(User user) {
        return accountRepository.findAllByUserId(user.getId());
    }

    public Account getAccount(Long id, User user) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to access this account");
        }
        return account;
    }

    public List<Transaction> getMiniStatement(Long id, User user) {
        Account account = getAccount(id, user);
        return transactionRepository
                .searchAccountTransactions(
                        account,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        org.springframework.data.domain.PageRequest.of(0, 5)
                )
                .getContent();
    }

    @Transactional
    public Account updateControls(Long accountId, AccountControlDto.UpdateRequest request, User user) {
        Account account = getAccount(accountId, user);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot be changed");
        }
        if (request.displayName() != null) {
            account.setDisplayName(request.displayName().trim());
        }
        if (request.dailyTransferLimit() != null) {
            account.setDailyTransferLimit(request.dailyTransferLimit());
        }
        if (request.dailyWithdrawalLimit() != null) {
            account.setDailyWithdrawalLimit(request.dailyWithdrawalLimit());
        }
        if (request.onlineTransactionsEnabled() != null) {
            account.setOnlineTransactionsEnabled(request.onlineTransactionsEnabled());
        }
        return accountRepository.save(account);
    }

    @Transactional
    public Account freeze(Long accountId, User user) {
        Account account = getAccount(accountId, user);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot be frozen");
        }
        account.setStatus(Account.AccountStatus.FROZEN);
        return accountRepository.save(account);
    }

    @Transactional
    public Account unfreeze(Long accountId, User user) {
        Account account = getAccount(accountId, user);
        if (account.getStatus() != Account.AccountStatus.FROZEN) {
            throw new IllegalStateException("Only frozen accounts can be unfrozen");
        }
        account.setStatus(Account.AccountStatus.ACTIVE);
        return accountRepository.save(account);
    }

    @Transactional
    public void close(Long accountId, User user) {
        Account account = getAccount(accountId, user);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account balance must be zero before closing");
        }
        account.setStatus(Account.AccountStatus.CLOSED);
        accountRepository.save(account);
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = AccountNumberGenerator.generate();
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account number");
    }
}
