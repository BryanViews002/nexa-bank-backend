package com.example.bank.service;

import com.example.bank.dto.AccountResponse;
import com.example.bank.dto.BankMapper;
import com.example.bank.dto.DashboardResponse;
import com.example.bank.dto.ScheduledPaymentDto;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.entity.Account;
import com.example.bank.entity.User;
import com.example.bank.repository.ScheduledPaymentRepository;
import com.example.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final NotificationService notificationService;
    private final KycGuardService kycGuardService;
    private final BankMapper bankMapper;

    public DashboardService(
            AccountService accountService,
            TransactionRepository transactionRepository,
            ScheduledPaymentRepository scheduledPaymentRepository,
            NotificationService notificationService,
            KycGuardService kycGuardService,
            BankMapper bankMapper
    ) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.scheduledPaymentRepository = scheduledPaymentRepository;
        this.notificationService = notificationService;
        this.kycGuardService = kycGuardService;
        this.bankMapper = bankMapper;
    }

    public DashboardResponse getDashboard(User user) {
        List<Account> accounts = accountService.getUserAccounts(user);
        List<AccountResponse> accountResponses = accounts.stream().map(bankMapper::toAccountResponse).toList();
        Map<String, BigDecimal> totals = accounts.stream().collect(Collectors.groupingBy(
                Account::getCurrency,
                Collectors.reducing(BigDecimal.ZERO, Account::getBalance, BigDecimal::add)
        ));
        List<TransactionResponse> recentTransactions = accounts.isEmpty()
                ? List.of()
                : transactionRepository
                        .findTop10ByFromAccountInOrToAccountInOrderByDateDesc(accounts, accounts)
                        .stream()
                        .map(bankMapper::toTransactionResponse)
                        .toList();
        List<ScheduledPaymentDto.Response> upcomingPayments = scheduledPaymentRepository
                .findByAccountFromUserIdOrderByNextRunAsc(user.getId())
                .stream()
                .filter(payment -> payment.isEnabled())
                .limit(5)
                .map(payment -> new ScheduledPaymentDto.Response(
                        payment.getId(),
                        payment.getAccountFrom().getId(),
                        bankMapper.maskAccountNumber(payment.getAccountTo()),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getIntervalDays(),
                        payment.getNextRun(),
                        payment.getLastRun(),
                        payment.isEnabled(),
                        payment.getDescription(),
                        payment.getCategory(),
                        payment.getLastError(),
                        payment.getFailureCount(),
                        payment.getMaxFailures(),
                        payment.getCreatedAt()
                ))
                .toList();

        return new DashboardResponse(
                kycGuardService.getCurrentStatus(user).name(),
                totals,
                accountResponses,
                recentTransactions,
                upcomingPayments,
                notificationService.unreadCount(user),
                Instant.now()
        );
    }
}
