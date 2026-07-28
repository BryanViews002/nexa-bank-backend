package com.example.bank.service;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.SavingsGoalDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Notification;
import com.example.bank.entity.SavingsGoal;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.SavingsGoalRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SavingsGoalService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final SavingsGoalRepository savingsGoalRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final KycGuardService kycGuardService;
    private final NotificationService notificationService;
    private final BankMapper bankMapper;

    public SavingsGoalService(
            SavingsGoalRepository savingsGoalRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            TransactionService transactionService,
            KycGuardService kycGuardService,
            NotificationService notificationService,
            BankMapper bankMapper
    ) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.kycGuardService = kycGuardService;
        this.notificationService = notificationService;
        this.bankMapper = bankMapper;
    }

    @Transactional
    public SavingsGoalDto.Response create(SavingsGoalDto.CreateRequest request, User user) {
        kycGuardService.requireApproved(user);
        Account funding = ownedAccount(request.fundingAccountId(), user);
        if (funding.getType() == Account.AccountType.GOAL) {
            throw new IllegalArgumentException("A savings goal cannot be funded by another goal account");
        }
        if (funding.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Funding account is not active");
        }

        Account goalAccount = accountService.openAccount(
                user,
                Account.AccountType.GOAL.name(),
                BigDecimal.ZERO,
                funding.getCurrency(),
                request.name().trim()
        );

        SavingsGoal goal = new SavingsGoal();
        goal.setUser(user);
        goal.setFundingAccount(funding);
        goal.setGoalAccount(goalAccount);
        goal.setName(request.name().trim());
        goal.setDescription(request.description());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        applyAutoContribution(
                goal,
                request.autoContributionAmount(),
                request.autoContributionIntervalDays()
        );
        goal = savingsGoalRepository.save(goal);

        if (request.initialContribution() != null && request.initialContribution().signum() > 0) {
            moveMoney(
                    goal,
                    funding,
                    goalAccount,
                    request.initialContribution(),
                    Transaction.TransactionType.GOAL_CONTRIBUTION,
                    "goal-open:" + goal.getId(),
                    "Initial contribution to " + goal.getName(),
                    true
            );
            goal = refresh(goal.getId());
        }
        return toResponse(goal);
    }

    public List<SavingsGoalDto.Response> list(User user) {
        return savingsGoalRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SavingsGoalDto.Response get(Long id, User user) {
        return toResponse(owned(id, user));
    }

    @Transactional
    public SavingsGoalDto.Response update(Long id, SavingsGoalDto.UpdateRequest request, User user) {
        SavingsGoal goal = owned(id, user);
        requireActive(goal);
        if (request.name() != null && !request.name().isBlank()) {
            goal.setName(request.name().trim());
        }
        if (request.description() != null) {
            goal.setDescription(request.description());
        }
        if (request.targetAmount() != null) {
            goal.setTargetAmount(request.targetAmount());
        }
        if (request.targetDate() != null) {
            goal.setTargetDate(request.targetDate());
        }
        if (request.autoContributionAmount() != null || request.autoContributionIntervalDays() != null) {
            applyAutoContribution(
                    goal,
                    request.autoContributionAmount() == null
                            ? goal.getAutoContributionAmount()
                            : request.autoContributionAmount(),
                    request.autoContributionIntervalDays() == null
                            ? goal.getAutoContributionIntervalDays()
                            : request.autoContributionIntervalDays()
            );
        }
        return toResponse(savingsGoalRepository.save(goal));
    }

    @Transactional
    public SavingsGoalDto.Response contribute(Long id, BigDecimal amount, User user, String idempotencyKey) {
        SavingsGoal goal = owned(id, user);
        requireActive(goal);
        moveMoney(
                goal,
                goal.getFundingAccount(),
                goal.getGoalAccount(),
                amount,
                Transaction.TransactionType.GOAL_CONTRIBUTION,
                idempotencyKey,
                "Contribution to " + goal.getName(),
                true
        );
        return toResponse(refresh(id));
    }

    @Transactional
    public SavingsGoalDto.Response withdraw(Long id, BigDecimal amount, User user, String idempotencyKey) {
        SavingsGoal goal = owned(id, user);
        if (goal.getStatus() == SavingsGoal.GoalStatus.CANCELLED) {
            throw new IllegalStateException("This savings goal has been cancelled");
        }
        moveMoney(
                goal,
                goal.getGoalAccount(),
                goal.getFundingAccount(),
                amount,
                Transaction.TransactionType.GOAL_WITHDRAWAL,
                idempotencyKey,
                "Withdrawal from " + goal.getName(),
                false
        );
        SavingsGoal refreshed = refresh(id);
        if (refreshed.getStatus() == SavingsGoal.GoalStatus.COMPLETED
                && balanceOf(refreshed).compareTo(refreshed.getTargetAmount()) < 0) {
            refreshed.setStatus(SavingsGoal.GoalStatus.ACTIVE);
            refreshed = savingsGoalRepository.save(refreshed);
        }
        return toResponse(refreshed);
    }

    @Transactional
    public SavingsGoalDto.Response cancel(Long id, User user) {
        SavingsGoal goal = owned(id, user);
        if (goal.getStatus() == SavingsGoal.GoalStatus.CANCELLED) {
            throw new IllegalStateException("This savings goal is already cancelled");
        }
        BigDecimal balance = balanceOf(goal);
        if (balance.signum() > 0) {
            moveMoney(
                    goal,
                    goal.getGoalAccount(),
                    goal.getFundingAccount(),
                    balance,
                    Transaction.TransactionType.GOAL_WITHDRAWAL,
                    "goal-cancel:" + goal.getId(),
                    "Closing balance returned from " + goal.getName(),
                    false
            );
        }
        SavingsGoal refreshed = refresh(id);
        refreshed.setStatus(SavingsGoal.GoalStatus.CANCELLED);
        refreshed.setNextAutoContribution(null);
        refreshed = savingsGoalRepository.save(refreshed);

        Account goalAccount = accountRepository.findById(refreshed.getGoalAccount().getId()).orElseThrow();
        goalAccount.setStatus(Account.AccountStatus.CLOSED);
        accountRepository.save(goalAccount);
        return toResponse(refreshed);
    }

    /**
     * Drives automatic contributions. Each goal runs in its own transaction so one failure
     * (for example insufficient funds) does not abort the rest of the batch.
     */
    @Transactional
    public void runAutoContribution(Long goalId) {
        SavingsGoal goal = savingsGoalRepository.findById(goalId).orElse(null);
        if (goal == null
                || goal.getStatus() != SavingsGoal.GoalStatus.ACTIVE
                || goal.getAutoContributionAmount() == null
                || goal.getNextAutoContribution() == null
                || goal.getNextAutoContribution().isAfter(Instant.now())) {
            return;
        }
        int intervalDays = goal.getAutoContributionIntervalDays() == null
                ? 30
                : goal.getAutoContributionIntervalDays();
        try {
            moveMoney(
                    goal,
                    goal.getFundingAccount(),
                    goal.getGoalAccount(),
                    goal.getAutoContributionAmount(),
                    Transaction.TransactionType.GOAL_CONTRIBUTION,
                    "goal-auto:" + goal.getId() + ":" + goal.getNextAutoContribution(),
                    "Automatic contribution to " + goal.getName(),
                    true
            );
        } catch (RuntimeException exception) {
            notificationService.notify(
                    goal.getUser(),
                    Notification.NotificationType.SAVINGS_GOAL,
                    "Automatic contribution failed",
                    "We could not move money into \"" + goal.getName() + "\": " + exception.getMessage(),
                    "SAVINGS_GOAL",
                    goal.getId()
            );
        }
        SavingsGoal refreshed = refresh(goalId);
        refreshed.setNextAutoContribution(Instant.now().plus(intervalDays, ChronoUnit.DAYS));
        savingsGoalRepository.save(refreshed);
    }

    public List<SavingsGoal> findDueAutoContributions() {
        return savingsGoalRepository.findByStatusAndNextAutoContributionLessThanEqual(
                SavingsGoal.GoalStatus.ACTIVE,
                Instant.now()
        );
    }

    private void moveMoney(
            SavingsGoal goal,
            Account source,
            Account destination,
            BigDecimal amount,
            Transaction.TransactionType type,
            String idempotencyKey,
            String description,
            boolean enforceDailyLimit
    ) {
        transactionService.transferForPurpose(
                source.getId(),
                destination.getAccountNumber(),
                amount,
                goal.getUser(),
                idempotencyKey,
                description,
                "SAVINGS",
                type,
                enforceDailyLimit
        );
        if (type == Transaction.TransactionType.GOAL_CONTRIBUTION) {
            markCompletedIfReached(goal.getId());
        }
    }

    private void markCompletedIfReached(Long goalId) {
        SavingsGoal goal = refresh(goalId);
        if (goal.getStatus() != SavingsGoal.GoalStatus.ACTIVE) {
            return;
        }
        if (balanceOf(goal).compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(SavingsGoal.GoalStatus.COMPLETED);
            goal.setNextAutoContribution(null);
            savingsGoalRepository.save(goal);
            notificationService.notify(
                    goal.getUser(),
                    Notification.NotificationType.SAVINGS_GOAL,
                    "Savings goal reached",
                    "You reached your target for \"" + goal.getName() + "\".",
                    "SAVINGS_GOAL",
                    goal.getId()
            );
        }
    }

    private void applyAutoContribution(SavingsGoal goal, BigDecimal amount, Integer intervalDays) {
        if (amount == null || intervalDays == null) {
            goal.setAutoContributionAmount(null);
            goal.setAutoContributionIntervalDays(null);
            goal.setNextAutoContribution(null);
            return;
        }
        goal.setAutoContributionAmount(amount);
        goal.setAutoContributionIntervalDays(intervalDays);
        goal.setNextAutoContribution(Instant.now().plus(intervalDays, ChronoUnit.DAYS));
    }

    private BigDecimal balanceOf(SavingsGoal goal) {
        return accountRepository.findById(goal.getGoalAccount().getId())
                .map(Account::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private SavingsGoal refresh(Long id) {
        return savingsGoalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Savings goal not found"));
    }

    private SavingsGoal owned(Long id, User user) {
        return savingsGoalRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Savings goal not found"));
    }

    private void requireActive(SavingsGoal goal) {
        if (goal.getStatus() != SavingsGoal.GoalStatus.ACTIVE) {
            throw new IllegalStateException("This savings goal is " + goal.getStatus().name().toLowerCase());
        }
    }

    private Account ownedAccount(Long accountId, User user) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to use this account");
        }
        return account;
    }

    private SavingsGoalDto.Response toResponse(SavingsGoal goal) {
        BigDecimal saved = balanceOf(goal);
        BigDecimal remaining = goal.getTargetAmount().subtract(saved).max(BigDecimal.ZERO);
        BigDecimal progress = goal.getTargetAmount().signum() == 0
                ? BigDecimal.ZERO
                : saved.multiply(HUNDRED)
                        .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP)
                        .min(HUNDRED);
        return new SavingsGoalDto.Response(
                goal.getId(),
                goal.getName(),
                goal.getDescription(),
                goal.getTargetAmount(),
                saved,
                remaining,
                progress,
                goal.getGoalAccount().getCurrency(),
                goal.getTargetDate(),
                goal.getFundingAccount().getId(),
                goal.getGoalAccount().getId(),
                bankMapper.maskAccountNumber(goal.getGoalAccount().getAccountNumber()),
                goal.getAutoContributionAmount(),
                goal.getAutoContributionIntervalDays(),
                goal.getNextAutoContribution(),
                goal.getStatus().name(),
                goal.getCreatedAt()
        );
    }
}
