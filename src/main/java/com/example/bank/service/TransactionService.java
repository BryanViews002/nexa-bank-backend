package com.example.bank.service;

import com.example.bank.entity.Account;
import com.example.bank.entity.LedgerAccount;
import com.example.bank.entity.LedgerPosting;
import com.example.bank.entity.ScheduledPayment;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.exception.InsufficientFundsException;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final List<Transaction.TransactionType> TRANSFER_LIMIT_TYPES = List.of(
            Transaction.TransactionType.TRANSFER,
            Transaction.TransactionType.SCHEDULED_PAYMENT,
            Transaction.TransactionType.GOAL_CONTRIBUTION,
            Transaction.TransactionType.CARD_PURCHASE,
            Transaction.TransactionType.EXTERNAL_PAYOUT,
            Transaction.TransactionType.LOAN_REPAYMENT,
            Transaction.TransactionType.FX_EXCHANGE
    );

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final KycGuardService kycGuardService;
    private final NotificationService notificationService;
    private final BudgetService budgetService;

    public TransactionService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            AuditService auditService,
            LedgerService ledgerService,
            IdempotencyService idempotencyService,
            KycGuardService kycGuardService,
            NotificationService notificationService,
            BudgetService budgetService
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.kycGuardService = kycGuardService;
        this.notificationService = notificationService;
        this.budgetService = budgetService;
    }

    public List<Transaction> getAllUserTransactions(User user) {
        List<Account> accounts = accountRepository.findAllByUserId(user.getId());
        if (accounts.isEmpty()) {
            return List.of();
        }
        return transactionRepository.findTop10ByFromAccountInOrToAccountInOrderByDateDesc(accounts, accounts);
    }

    @Transactional
    public Transaction deposit(
            Long accountId,
            BigDecimal amount,
            User actor,
            String idempotencyKey,
            String description,
            String category
    ) {
        if (!hasRole(actor, "ROLE_ADMIN")) {
            throw new AccessDeniedException("Direct account credits are restricted to administrators");
        }
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        kycGuardService.requireApproved(account.getUser());
        validateAmount(amount);
        validateAccountActive(account);

        String key = idempotencyService.normalizeKey(idempotencyKey);
        String payload = accountId + "|" + amount + "|" + description;
        Transaction existing = findExisting(actor.getId(), "ADMIN_DEPOSIT", key, payload);
        if (existing != null) {
            return existing;
        }

        Transaction transaction = createTransaction(
                null,
                account,
                amount,
                account.getCurrency(),
                Transaction.TransactionType.DEPOSIT,
                actor.getId(),
                key,
                defaultText(description, "Administrative account funding"),
                defaultText(category, "FUNDING")
        );

        LedgerAccount cash = ledgerService.systemAccount(
                "CASH",
                account.getCurrency(),
                LedgerAccount.LedgerAccountType.ASSET
        );
        LedgerAccount customer = ledgerService.ensureCustomerAccount(account);
        ledgerService.post(
                transaction,
                "ACCOUNT_FUNDING",
                transaction.getDescription(),
                List.of(
                        posting(cash, LedgerPosting.PostingDirection.DEBIT, amount, account.getCurrency()),
                        posting(customer, LedgerPosting.PostingDirection.CREDIT, amount, account.getCurrency())
                )
        );

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        complete(transaction);
        recordIdempotency(actor.getId(), "ADMIN_DEPOSIT", key, payload, transaction);
        auditService.log(actor.getId(), "DEPOSIT_COMPLETED", Map.of(
                "transactionId", transaction.getId(),
                "accountId", accountId,
                "amount", amount
        ));
        notifyTransaction(account.getUser(), transaction);
        return transaction;
    }

    @Transactional
    public Transaction withdraw(
            Long accountId,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String description,
            String category
    ) {
        kycGuardService.requireApproved(user);
        validateAmount(amount);

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        requireOwner(account, user);
        validateAccountActive(account);
        validateOnlineTransactions(account);
        enforceDailyLimit(account, amount, account.getDailyWithdrawalLimit(), List.of(
                Transaction.TransactionType.WITHDRAW
        ));
        requireSufficientFunds(account, amount);

        String key = idempotencyService.normalizeKey(idempotencyKey);
        String payload = accountId + "|" + amount + "|" + description;
        Transaction existing = findExisting(user.getId(), "WITHDRAW", key, payload);
        if (existing != null) {
            return existing;
        }

        Transaction transaction = createTransaction(
                account,
                null,
                amount,
                account.getCurrency(),
                Transaction.TransactionType.WITHDRAW,
                user.getId(),
                key,
                defaultText(description, "Account withdrawal"),
                defaultText(category, "CASH")
        );

        LedgerAccount customer = ledgerService.ensureCustomerAccount(account);
        LedgerAccount cash = ledgerService.systemAccount(
                "CASH",
                account.getCurrency(),
                LedgerAccount.LedgerAccountType.ASSET
        );
        ledgerService.post(
                transaction,
                "ACCOUNT_WITHDRAWAL",
                transaction.getDescription(),
                List.of(
                        posting(customer, LedgerPosting.PostingDirection.DEBIT, amount, account.getCurrency()),
                        posting(cash, LedgerPosting.PostingDirection.CREDIT, amount, account.getCurrency())
                )
        );

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        complete(transaction);
        recordIdempotency(user.getId(), "WITHDRAW", key, payload, transaction);
        auditService.log(user.getId(), "WITHDRAW_COMPLETED", Map.of(
                "transactionId", transaction.getId(),
                "accountId", accountId,
                "amount", amount
        ));
        notifyTransaction(user, transaction);
        evaluateBudget(user, transaction);
        return transaction;
    }

    @Transactional
    public Transaction transfer(
            Long fromAccountId,
            String toIdentifier,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String description,
            String category
    ) {
        return transferForPurpose(
                fromAccountId,
                toIdentifier,
                amount,
                user,
                idempotencyKey,
                description,
                category,
                Transaction.TransactionType.TRANSFER,
                true
        );
    }

    @Transactional
    public Transaction transferForPurpose(
            Long fromAccountId,
            String toIdentifier,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String description,
            String category,
            Transaction.TransactionType type,
            boolean enforceDailyLimit
    ) {
        kycGuardService.requireApproved(user);
        validateAmount(amount);

        Account destinationPreview = accountRepository.findByAccountNumber(toIdentifier)
                .or(() -> accountRepository.findFirstByUserUsernameAndStatusOrderByCreatedAtAsc(
                        toIdentifier,
                        Account.AccountStatus.ACTIVE
                ))
                .orElseThrow(() -> new IllegalArgumentException("Recipient account not found"));
        if (fromAccountId.equals(destinationPreview.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }

        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdate(
                List.of(fromAccountId, destinationPreview.getId())
        );
        if (lockedAccounts.size() != 2) {
            throw new IllegalArgumentException("One or more accounts could not be found");
        }
        Account source = lockedAccounts.stream()
                .filter(account -> account.getId().equals(fromAccountId))
                .findFirst()
                .orElseThrow();
        Account destination = lockedAccounts.stream()
                .filter(account -> account.getId().equals(destinationPreview.getId()))
                .findFirst()
                .orElseThrow();

        requireOwner(source, user);
        validateAccountActive(source);
        validateAccountActive(destination);
        validateOnlineTransactions(source);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new IllegalArgumentException("Use the FX endpoint for cross-currency transfers");
        }
        if (enforceDailyLimit) {
            enforceDailyLimit(source, amount, source.getDailyTransferLimit(), TRANSFER_LIMIT_TYPES);
        }
        requireSufficientFunds(source, amount);

        String operation = type.name();
        String key = idempotencyService.normalizeKey(idempotencyKey);
        String payload = fromAccountId + "|" + destination.getId() + "|" + amount + "|" + description;
        Transaction existing = findExisting(user.getId(), operation, key, payload);
        if (existing != null) {
            return existing;
        }

        Transaction transaction = createTransaction(
                source,
                destination,
                amount,
                source.getCurrency(),
                type,
                user.getId(),
                key,
                defaultText(description, "Transfer to " + destination.getAccountNumber()),
                defaultText(category, "TRANSFER")
        );

        LedgerAccount sourceLedger = ledgerService.ensureCustomerAccount(source);
        LedgerAccount destinationLedger = ledgerService.ensureCustomerAccount(destination);
        ledgerService.post(
                transaction,
                type.name(),
                transaction.getDescription(),
                List.of(
                        posting(sourceLedger, LedgerPosting.PostingDirection.DEBIT, amount, source.getCurrency()),
                        posting(destinationLedger, LedgerPosting.PostingDirection.CREDIT, amount, source.getCurrency())
                )
        );

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        accountRepository.saveAll(List.of(source, destination));
        complete(transaction);
        recordIdempotency(user.getId(), operation, key, payload, transaction);
        auditService.log(user.getId(), type.name() + "_COMPLETED", Map.of(
                "transactionId", transaction.getId(),
                "fromAccountId", source.getId(),
                "toAccountId", destination.getId(),
                "amount", amount
        ));
        notifyTransaction(source.getUser(), transaction);
        notifyTransaction(destination.getUser(), transaction);
        evaluateBudget(source.getUser(), transaction);
        return transaction;
    }

    @Transactional
    public Transaction creditFromSystem(
            Account target,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String systemPurpose,
            Transaction.TransactionType type,
            String description
    ) {
        validateAmount(amount);
        Account lockedTarget = accountRepository.findByIdForUpdate(target.getId()).orElseThrow();
        validateAccountActive(lockedTarget);

        String operation = type.name();
        String key = idempotencyService.normalizeKey(idempotencyKey);
        String payload = target.getId() + "|" + amount + "|" + systemPurpose;
        Transaction existing = findExisting(user.getId(), operation, key, payload);
        if (existing != null) {
            return existing;
        }

        Transaction transaction = createTransaction(
                null,
                lockedTarget,
                amount,
                lockedTarget.getCurrency(),
                type,
                user.getId(),
                key,
                description,
                type.name()
        );
        LedgerAccount system = ledgerService.systemAccount(
                systemPurpose,
                lockedTarget.getCurrency(),
                LedgerAccount.LedgerAccountType.ASSET
        );
        LedgerAccount customer = ledgerService.ensureCustomerAccount(lockedTarget);
        ledgerService.post(
                transaction,
                type.name(),
                description,
                List.of(
                        posting(system, LedgerPosting.PostingDirection.DEBIT, amount, lockedTarget.getCurrency()),
                        posting(customer, LedgerPosting.PostingDirection.CREDIT, amount, lockedTarget.getCurrency())
                )
        );
        lockedTarget.setBalance(lockedTarget.getBalance().add(amount));
        accountRepository.save(lockedTarget);
        complete(transaction);
        recordIdempotency(user.getId(), operation, key, payload, transaction);
        notifyTransaction(lockedTarget.getUser(), transaction);
        return transaction;
    }

    @Transactional
    public Transaction debitToSystem(
            Account source,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String systemPurpose,
            Transaction.TransactionType type,
            String description,
            boolean enforceLimit
    ) {
        return debitToSystem(
                source,
                amount,
                user,
                idempotencyKey,
                systemPurpose,
                type,
                description,
                enforceLimit,
                true
        );
    }

    /**
     * Bank-initiated debits (a dispute clawback, for example) pass {@code enforceKyc = false}:
     * the movement is the bank's decision, not the customer's, so it must not be blocked by
     * the customer's own KYC state.
     */
    @Transactional
    public Transaction debitToSystem(
            Account source,
            BigDecimal amount,
            User user,
            String idempotencyKey,
            String systemPurpose,
            Transaction.TransactionType type,
            String description,
            boolean enforceLimit,
            boolean enforceKyc
    ) {
        if (enforceKyc) {
            kycGuardService.requireApproved(user);
        }
        validateAmount(amount);
        Account lockedSource = accountRepository.findByIdForUpdate(source.getId()).orElseThrow();
        requireOwner(lockedSource, user);
        validateAccountActive(lockedSource);
        validateOnlineTransactions(lockedSource);
        if (enforceLimit) {
            enforceDailyLimit(
                    lockedSource,
                    amount,
                    lockedSource.getDailyTransferLimit(),
                    TRANSFER_LIMIT_TYPES
            );
        }
        requireSufficientFunds(lockedSource, amount);

        String operation = type.name();
        String key = idempotencyService.normalizeKey(idempotencyKey);
        String payload = source.getId() + "|" + amount + "|" + systemPurpose;
        Transaction existing = findExisting(user.getId(), operation, key, payload);
        if (existing != null) {
            return existing;
        }

        Transaction transaction = createTransaction(
                lockedSource,
                null,
                amount,
                lockedSource.getCurrency(),
                type,
                user.getId(),
                key,
                description,
                type.name()
        );
        LedgerAccount customer = ledgerService.ensureCustomerAccount(lockedSource);
        LedgerAccount system = ledgerService.systemAccount(
                systemPurpose,
                lockedSource.getCurrency(),
                LedgerAccount.LedgerAccountType.ASSET
        );
        ledgerService.post(
                transaction,
                type.name(),
                description,
                List.of(
                        posting(customer, LedgerPosting.PostingDirection.DEBIT, amount, lockedSource.getCurrency()),
                        posting(system, LedgerPosting.PostingDirection.CREDIT, amount, lockedSource.getCurrency())
                )
        );
        lockedSource.setBalance(lockedSource.getBalance().subtract(amount));
        accountRepository.save(lockedSource);
        complete(transaction);
        recordIdempotency(user.getId(), operation, key, payload, transaction);
        notifyTransaction(user, transaction);
        evaluateBudget(user, transaction);
        return transaction;
    }

    public Page<Transaction> getHistory(
            Long accountId,
            User user,
            Transaction.TransactionType type,
            Transaction.TransactionStatus status,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String query,
            int page,
            int size
    ) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        requireOwner(account, user);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "date")
        );
        return transactionRepository.searchAccountTransactions(
                account,
                type,
                status,
                blankToNull(category),
                startDate,
                endDate,
                minAmount,
                maxAmount,
                blankToNull(query),
                pageable
        );
    }

    public List<Transaction> getHistory(Long accountId, User user, Map<String, String> params) {
        return getHistory(
                accountId,
                user,
                parseEnum(params.get("type"), Transaction.TransactionType.class),
                parseEnum(params.get("status"), Transaction.TransactionStatus.class),
                params.get("category"),
                parseDateTime(params.get("startDate"), false),
                parseDateTime(params.get("endDate"), true),
                parseBigDecimal(params.get("minAmount")),
                parseBigDecimal(params.get("maxAmount")),
                params.get("query"),
                parseInt(params.get("page"), 0),
                parseInt(params.get("size"), 50)
        ).getContent();
    }

    @Transactional
    public Transaction updateCategory(Long transactionId, String category, User user) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        boolean owner = transaction.getFromAccount() != null
                && transaction.getFromAccount().getUser().getId().equals(user.getId());
        owner = owner || transaction.getToAccount() != null
                && transaction.getToAccount().getUser().getId().equals(user.getId());
        if (!owner) {
            throw new AccessDeniedException("Not authorized to update this transaction");
        }
        transaction.setCategory(defaultText(category, "UNCATEGORIZED").toUpperCase(Locale.ROOT));
        return transactionRepository.save(transaction);
    }

    @Transactional
    public Transaction transferInternal(ScheduledPayment scheduledPayment) {
        String key = "scheduled:" + scheduledPayment.getId() + ":" + scheduledPayment.getNextRun();
        return transferForPurpose(
                scheduledPayment.getAccountFrom().getId(),
                scheduledPayment.getAccountTo(),
                scheduledPayment.getAmount(),
                scheduledPayment.getAccountFrom().getUser(),
                key,
                "Scheduled payment",
                "BILLS",
                Transaction.TransactionType.SCHEDULED_PAYMENT,
                true
        );
    }

    private Transaction createTransaction(
            Account source,
            Account destination,
            BigDecimal amount,
            String currency,
            Transaction.TransactionType type,
            Long initiatedBy,
            String idempotencyKey,
            String description,
            String category
    ) {
        Transaction transaction = new Transaction();
        transaction.setFromAccount(source);
        transaction.setToAccount(destination);
        transaction.setAmount(amount);
        transaction.setFee(BigDecimal.ZERO);
        transaction.setCurrency(currency);
        transaction.setType(type);
        transaction.setStatus(Transaction.TransactionStatus.PENDING);
        transaction.setInitiatedByUserId(initiatedBy);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setDescription(description);
        transaction.setCategory(category);
        return transactionRepository.save(transaction);
    }

    private void complete(Transaction transaction) {
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transactionRepository.save(transaction);
    }

    private Transaction findExisting(Long userId, String operation, String key, String payload) {
        return idempotencyService.findExisting(userId, operation, key, payload)
                .map(id -> transactionRepository.findById(id)
                        .orElseThrow(() -> new IllegalStateException("Idempotent resource no longer exists")))
                .orElse(null);
    }

    private void recordIdempotency(
            Long userId,
            String operation,
            String key,
            String payload,
            Transaction transaction
    ) {
        idempotencyService.record(
                userId,
                operation,
                key,
                payload,
                "TRANSACTION",
                transaction.getId()
        );
    }

    private LedgerService.PostingRequest posting(
            LedgerAccount account,
            LedgerPosting.PostingDirection direction,
            BigDecimal amount,
            String currency
    ) {
        return new LedgerService.PostingRequest(account, direction, amount, currency);
    }

    /**
     * Budget tracking only applies to money leaving the customer's own accounts, so this is
     * called from the debit side of an operation and never for incoming credits.
     */
    private void evaluateBudget(User user, Transaction transaction) {
        budgetService.evaluateAfterSpend(user, transaction.getCategory());
    }

    private void notifyTransaction(User user, Transaction transaction) {
        notificationService.notify(
                user,
                com.example.bank.entity.Notification.NotificationType.TRANSACTION,
                "Transaction completed",
                transaction.getType().name().replace('_', ' ') + " of "
                        + transaction.getAmount() + " " + transaction.getCurrency()
                        + " was completed.",
                "TRANSACTION",
                transaction.getId()
        );
    }

    private void enforceDailyLimit(
            Account account,
            BigDecimal requestedAmount,
            BigDecimal limit,
            List<Transaction.TransactionType> types
    ) {
        BigDecimal alreadyUsed = transactionRepository.sumCompletedOutgoingSince(
                account,
                types,
                LocalDate.now().atStartOfDay()
        );
        if (alreadyUsed.add(requestedAmount).compareTo(limit) > 0) {
            throw new IllegalArgumentException(
                    "Daily transaction limit exceeded. Limit: " + limit + ", used: " + alreadyUsed
            );
        }
    }

    private void requireSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + account.getBalance() + ", requested: " + amount
            );
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > 4) {
            throw new IllegalArgumentException("Amount cannot have more than four decimal places");
        }
    }

    private void validateAccountActive(Account account) {
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private void validateOnlineTransactions(Account account) {
        if (!account.isOnlineTransactionsEnabled()) {
            throw new IllegalStateException("Online transactions are disabled for this account");
        }
    }

    private void requireOwner(Account account, User user) {
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to use this account");
        }
    }

    private boolean hasRole(User user, String role) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(role));
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private LocalDateTime parseDateTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() == 10) {
            LocalDate date = LocalDate.parse(value);
            return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
        }
        return LocalDateTime.parse(value);
    }

    private int parseInt(String value, int fallback) {
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        return value == null || value.isBlank()
                ? null
                : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}
