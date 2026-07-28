package com.example.bank.service;

import com.example.bank.dto.BudgetDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Budget;
import com.example.bank.entity.Notification;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.BudgetRepository;
import com.example.bank.repository.TransactionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BudgetService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetRepository budgetRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    public BudgetService(
            BudgetRepository budgetRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            NotificationService notificationService
    ) {
        this.budgetRepository = budgetRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public BudgetDto.Response upsert(BudgetDto.UpsertRequest request, User user) {
        String category = normalizeCategory(request.category());
        LocalDate periodStart = startOfMonth(request.periodStart());

        Budget budget = budgetRepository
                .findByUserIdAndCategoryAndPeriodStart(user.getId(), category, periodStart)
                .orElseGet(() -> {
                    Budget created = new Budget();
                    created.setUser(user);
                    created.setCategory(category);
                    created.setPeriodStart(periodStart);
                    created.setCurrency(primaryCurrency(user));
                    return created;
                });
        budget.setMonthlyLimit(request.monthlyLimit());
        if (request.alertThreshold() != null) {
            budget.setAlertThreshold(request.alertThreshold());
        }
        budget.setActive(true);
        return toResponse(budgetRepository.save(budget), user);
    }

    public List<BudgetDto.Response> list(User user) {
        return budgetRepository.findByUserIdOrderByCategoryAsc(user.getId())
                .stream()
                .map(budget -> toResponse(budget, user))
                .toList();
    }

    @Transactional
    public BudgetDto.Response update(Long id, BudgetDto.UpdateRequest request, User user) {
        Budget budget = owned(id, user);
        if (request.monthlyLimit() != null) {
            budget.setMonthlyLimit(request.monthlyLimit());
        }
        if (request.alertThreshold() != null) {
            budget.setAlertThreshold(request.alertThreshold());
        }
        if (request.active() != null) {
            budget.setActive(request.active());
        }
        return toResponse(budgetRepository.save(budget), user);
    }

    @Transactional
    public void delete(Long id, User user) {
        budgetRepository.delete(owned(id, user));
    }

    public BudgetDto.Summary summary(User user, LocalDate month) {
        LocalDate periodStart = startOfMonth(month);
        LocalDate periodEnd = periodStart.plusMonths(1);
        List<Account> accounts = accountRepository.findAllByUserId(user.getId());

        List<BudgetDto.Response> budgets = budgetRepository
                .findByUserIdAndPeriodStartOrderByCategoryAsc(user.getId(), periodStart)
                .stream()
                .map(budget -> toResponse(budget, user))
                .toList();

        BigDecimal totalBudgeted = budgets.stream()
                .map(BudgetDto.Response::monthlyLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BudgetDto.CategorySpend> spendByCategory = new ArrayList<>();
        BigDecimal totalSpent = BigDecimal.ZERO;
        if (!accounts.isEmpty()) {
            List<Object[]> rows = transactionRepository.sumSpendGroupedByCategory(
                    accounts,
                    periodStart.atStartOfDay(),
                    periodEnd.atStartOfDay()
            );
            for (Object[] row : rows) {
                totalSpent = totalSpent.add((BigDecimal) row[1]);
            }
            for (Object[] row : rows) {
                BigDecimal amount = (BigDecimal) row[1];
                spendByCategory.add(new BudgetDto.CategorySpend(
                        (String) row[0],
                        amount,
                        percentOf(amount, totalSpent)
                ));
            }
        }

        return new BudgetDto.Summary(
                periodStart,
                periodEnd.minusDays(1),
                totalBudgeted,
                totalSpent,
                totalBudgeted.subtract(totalSpent),
                budgets,
                spendByCategory
        );
    }

    /**
     * Re-evaluates the budget for a category after money leaves an account and raises a single
     * alert per day once the configured threshold is crossed.
     */
    @Transactional
    public void evaluateAfterSpend(User user, String category) {
        if (category == null || category.isBlank()) {
            return;
        }
        LocalDate periodStart = startOfMonth(null);
        budgetRepository
                .findByUserIdAndCategoryAndPeriodStart(user.getId(), normalizeCategory(category), periodStart)
                .filter(Budget::isActive)
                .ifPresent(budget -> {
                    BigDecimal spent = spentFor(budget, user);
                    BigDecimal trigger = budget.getMonthlyLimit().multiply(budget.getAlertThreshold());
                    if (spent.compareTo(trigger) < 0) {
                        return;
                    }
                    if (budget.getLastAlertedAt() != null
                            && budget.getLastAlertedAt().isAfter(Instant.now().minus(1, ChronoUnit.DAYS))) {
                        return;
                    }
                    boolean over = spent.compareTo(budget.getMonthlyLimit()) > 0;
                    notificationService.notify(
                            user,
                            Notification.NotificationType.BUDGET,
                            over ? "Budget exceeded" : "Budget threshold reached",
                            "You have spent " + spent + " " + budget.getCurrency() + " of your "
                                    + budget.getMonthlyLimit() + " " + budget.getCurrency() + " "
                                    + budget.getCategory() + " budget this month.",
                            "BUDGET",
                            budget.getId()
                    );
                    budget.setLastAlertedAt(Instant.now());
                    budgetRepository.save(budget);
                });
    }

    private BigDecimal spentFor(Budget budget, User user) {
        List<Account> accounts = accountRepository.findAllByUserId(user.getId());
        if (accounts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return transactionRepository.sumSpendByCategory(
                accounts,
                budget.getCategory(),
                budget.getPeriodStart().atStartOfDay(),
                budget.getPeriodStart().plusMonths(1).atStartOfDay()
        );
    }

    private BudgetDto.Response toResponse(Budget budget, User user) {
        BigDecimal spent = spentFor(budget, user);
        BigDecimal limit = budget.getMonthlyLimit();
        BigDecimal trigger = limit.multiply(budget.getAlertThreshold());
        return new BudgetDto.Response(
                budget.getId(),
                budget.getCategory(),
                limit,
                spent,
                limit.subtract(spent),
                percentOf(spent, limit),
                budget.getAlertThreshold(),
                spent.compareTo(limit) > 0,
                spent.compareTo(trigger) >= 0,
                budget.getCurrency(),
                budget.getPeriodStart(),
                budget.getPeriodStart().plusMonths(1).minusDays(1),
                budget.isActive(),
                budget.getCreatedAt()
        );
    }

    private BigDecimal percentOf(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private String primaryCurrency(User user) {
        return accountRepository.findAllByUserId(user.getId()).stream()
                .findFirst()
                .map(Account::getCurrency)
                .orElse("USD");
    }

    private LocalDate startOfMonth(LocalDate date) {
        return (date == null ? LocalDate.now() : date).withDayOfMonth(1);
    }

    private String normalizeCategory(String category) {
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private Budget owned(Long id, User user) {
        return budgetRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Budget not found"));
    }
}
