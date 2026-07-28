package com.example.bank.service;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.ScheduledPaymentDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.ScheduledPayment;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.ScheduledPaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ScheduledPaymentService {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final AccountRepository accountRepository;
    private final KycGuardService kycGuardService;
    private final ScheduledPaymentExecutor scheduledPaymentExecutor;
    private final BankMapper bankMapper;

    public ScheduledPaymentService(
            ScheduledPaymentRepository scheduledPaymentRepository,
            AccountRepository accountRepository,
            KycGuardService kycGuardService,
            ScheduledPaymentExecutor scheduledPaymentExecutor,
            BankMapper bankMapper
    ) {
        this.scheduledPaymentRepository = scheduledPaymentRepository;
        this.accountRepository = accountRepository;
        this.kycGuardService = kycGuardService;
        this.scheduledPaymentExecutor = scheduledPaymentExecutor;
        this.bankMapper = bankMapper;
    }

    @Transactional
    public ScheduledPaymentDto.Response create(ScheduledPaymentDto.CreateRequest request, User user) {
        kycGuardService.requireApproved(user);
        Account source = accountRepository.findById(request.accountFromId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        if (!source.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to schedule payments from this account");
        }
        if (source.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account is not active");
        }
        if (source.getAccountNumber().equals(request.accountTo())) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }

        ScheduledPayment payment = new ScheduledPayment();
        payment.setAccountFrom(source);
        payment.setAccountTo(request.accountTo().trim());
        payment.setAmount(request.amount());
        payment.setCurrency(source.getCurrency());
        payment.setIntervalDays(request.intervalDays());
        payment.setNextRun(
                request.firstRun() == null
                        ? Instant.now().plus(1, ChronoUnit.DAYS)
                        : request.firstRun()
        );
        payment.setDescription(request.description());
        payment.setCategory(
                request.category() == null || request.category().isBlank()
                        ? "BILLS"
                        : request.category().trim().toUpperCase()
        );
        return toResponse(scheduledPaymentRepository.save(payment));
    }

    public List<ScheduledPaymentDto.Response> list(User user) {
        return scheduledPaymentRepository.findByAccountFromUserIdOrderByNextRunAsc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ScheduledPaymentDto.Response update(
            Long id,
            ScheduledPaymentDto.UpdateRequest request,
            User user
    ) {
        ScheduledPayment payment = owned(id, user);
        if (request.intervalDays() != null) {
            payment.setIntervalDays(request.intervalDays());
        }
        if (request.nextRun() != null) {
            payment.setNextRun(request.nextRun());
        }
        if (request.enabled() != null) {
            payment.setEnabled(request.enabled());
        }
        if (request.description() != null) {
            payment.setDescription(request.description().trim());
        }
        if (request.category() != null) {
            payment.setCategory(request.category().trim().toUpperCase());
        }
        if (request.maxFailures() != null) {
            payment.setMaxFailures(request.maxFailures());
        }
        return toResponse(scheduledPaymentRepository.save(payment));
    }

    @Transactional
    public void delete(Long id, User user) {
        scheduledPaymentRepository.delete(owned(id, user));
    }

    @Transactional
    public ScheduledPaymentDto.Response runNow(Long id, User user) {
        ScheduledPayment payment = owned(id, user);
        scheduledPaymentExecutor.execute(payment.getId(), true);
        return toResponse(
                scheduledPaymentRepository.findById(payment.getId()).orElseThrow()
        );
    }

    @Scheduled(fixedDelayString = "${app.scheduler.recurring-payment-delay-ms}")
    public void runDuePayments() {
        scheduledPaymentRepository.findDuePayments()
                .forEach(payment -> scheduledPaymentExecutor.execute(payment.getId(), false));
    }

    private ScheduledPayment owned(Long id, User user) {
        return scheduledPaymentRepository.findByIdAndAccountFromUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Scheduled payment not found"));
    }

    private ScheduledPaymentDto.Response toResponse(ScheduledPayment payment) {
        return new ScheduledPaymentDto.Response(
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
        );
    }
}
