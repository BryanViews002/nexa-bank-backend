package com.example.bank.service;

import com.example.bank.entity.Notification;
import com.example.bank.entity.ScheduledPayment;
import com.example.bank.entity.Transaction;
import com.example.bank.repository.ScheduledPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class ScheduledPaymentExecutor {

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;

    public ScheduledPaymentExecutor(
            ScheduledPaymentRepository scheduledPaymentRepository,
            TransactionService transactionService,
            NotificationService notificationService
    ) {
        this.scheduledPaymentRepository = scheduledPaymentRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(Long scheduledPaymentId, boolean manual) {
        ScheduledPayment payment = scheduledPaymentRepository.findByIdForUpdate(scheduledPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled payment not found"));
        if (!payment.isEnabled()) {
            return;
        }
        if (!manual && payment.getNextRun().isAfter(Instant.now())) {
            return;
        }

        try {
            Transaction transaction = transactionService.transferInternal(payment);
            payment.setLastRun(Instant.now());
            payment.setNextRun(payment.getNextRun().plus(payment.getIntervalDays(), ChronoUnit.DAYS));
            payment.setLastError(null);
            payment.setFailureCount(0);
            scheduledPaymentRepository.save(payment);
            notificationService.notify(
                    payment.getAccountFrom().getUser(),
                    Notification.NotificationType.SCHEDULED_PAYMENT,
                    "Scheduled payment completed",
                    "Your scheduled payment of " + payment.getAmount() + " " + payment.getCurrency()
                            + " was completed.",
                    "TRANSACTION",
                    transaction.getId()
            );
        } catch (RuntimeException exception) {
            payment.setFailureCount(payment.getFailureCount() + 1);
            payment.setLastError(exception.getMessage());
            payment.setNextRun(Instant.now().plus(payment.getIntervalDays(), ChronoUnit.DAYS));
            if (payment.getFailureCount() >= payment.getMaxFailures()) {
                payment.setEnabled(false);
            }
            scheduledPaymentRepository.save(payment);
            notificationService.notify(
                    payment.getAccountFrom().getUser(),
                    Notification.NotificationType.SCHEDULED_PAYMENT,
                    "Scheduled payment needs attention",
                    "A scheduled payment could not be completed: " + exception.getMessage(),
                    "SCHEDULED_PAYMENT",
                    payment.getId()
            );
        }
    }
}
