package com.example.bank.service;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.PaymentRequestDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Notification;
import com.example.bank.entity.PaymentRequest;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.PaymentRequestRepository;
import com.example.bank.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

@Service
public class PaymentRequestService {

    private static final int DEFAULT_EXPIRY_DAYS = 14;

    private final PaymentRequestRepository paymentRequestRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final KycGuardService kycGuardService;
    private final NotificationService notificationService;
    private final BankMapper bankMapper;

    public PaymentRequestService(
            PaymentRequestRepository paymentRequestRepository,
            AccountRepository accountRepository,
            UserRepository userRepository,
            TransactionService transactionService,
            KycGuardService kycGuardService,
            NotificationService notificationService,
            BankMapper bankMapper
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
        this.kycGuardService = kycGuardService;
        this.notificationService = notificationService;
        this.bankMapper = bankMapper;
    }

    @Transactional
    public PaymentRequestDto.Response create(PaymentRequestDto.CreateRequest request, User requester) {
        kycGuardService.requireApproved(requester);
        Account requesterAccount = accountRepository.findById(request.requesterAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!requesterAccount.getUser().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Not authorized to request money into this account");
        }
        if (requesterAccount.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Destination account is not active");
        }

        User payer = resolvePayer(request.payerIdentifier());
        if (payer.getId().equals(requester.getId())) {
            throw new IllegalArgumentException("You cannot request money from yourself");
        }

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setRequester(requester);
        paymentRequest.setRequesterAccount(requesterAccount);
        paymentRequest.setPayer(payer);
        paymentRequest.setAmount(request.amount());
        paymentRequest.setCurrency(requesterAccount.getCurrency());
        paymentRequest.setNote(request.note());
        paymentRequest.setExpiresAt(Instant.now().plus(
                request.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS : request.expiresInDays(),
                ChronoUnit.DAYS
        ));
        PaymentRequest saved = paymentRequestRepository.save(paymentRequest);

        notificationService.notify(
                payer,
                Notification.NotificationType.PAYMENT_REQUEST,
                "New payment request",
                requester.getFullName() + " requested " + saved.getAmount() + " " + saved.getCurrency()
                        + (saved.getNote() == null ? "." : " for: " + saved.getNote()),
                "PAYMENT_REQUEST",
                saved.getId()
        );
        return toResponse(saved, requester);
    }

    public List<PaymentRequestDto.Response> listIncoming(User user) {
        return paymentRequestRepository.findByPayerIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(request -> toResponse(request, user))
                .toList();
    }

    public List<PaymentRequestDto.Response> listOutgoing(User user) {
        return paymentRequestRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(request -> toResponse(request, user))
                .toList();
    }

    public List<PaymentRequestDto.Response> listAll(User user) {
        return Stream.concat(
                        paymentRequestRepository.findByPayerIdOrderByCreatedAtDesc(user.getId()).stream(),
                        paymentRequestRepository.findByRequesterIdOrderByCreatedAtDesc(user.getId()).stream()
                )
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(request -> toResponse(request, user))
                .toList();
    }

    @Transactional
    public PaymentRequestDto.Response accept(
            Long id,
            Long payerAccountId,
            User payer,
            String idempotencyKey
    ) {
        kycGuardService.requireApproved(payer);
        PaymentRequest request = paymentRequestRepository.findByIdAndPayerId(id, payer.getId())
                .orElseThrow(() -> new AccessDeniedException("Payment request not found"));
        requirePending(request);

        Account payerAccount = accountRepository.findById(payerAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!payerAccount.getUser().getId().equals(payer.getId())) {
            throw new AccessDeniedException("Not authorized to pay from this account");
        }
        if (!payerAccount.getCurrency().equals(request.getCurrency())) {
            throw new IllegalArgumentException(
                    "Pay from an account in " + request.getCurrency() + " to settle this request"
            );
        }

        Transaction transaction = transactionService.transferForPurpose(
                payerAccount.getId(),
                request.getRequesterAccount().getAccountNumber(),
                request.getAmount(),
                payer,
                idempotencyKey == null ? "payment-request:" + request.getId() : idempotencyKey,
                request.getNote() == null
                        ? "Payment request from " + request.getRequester().getFullName()
                        : request.getNote(),
                "PAYMENT_REQUEST",
                Transaction.TransactionType.PAYMENT_REQUEST,
                true
        );

        request.setStatus(PaymentRequest.RequestStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        request.setTransaction(transaction);
        PaymentRequest saved = paymentRequestRepository.save(request);

        notificationService.notify(
                request.getRequester(),
                Notification.NotificationType.PAYMENT_REQUEST,
                "Payment request paid",
                payer.getFullName() + " paid your request for " + request.getAmount() + " "
                        + request.getCurrency() + ".",
                "PAYMENT_REQUEST",
                saved.getId()
        );
        return toResponse(saved, payer);
    }

    @Transactional
    public PaymentRequestDto.Response decline(Long id, User payer) {
        PaymentRequest request = paymentRequestRepository.findByIdAndPayerId(id, payer.getId())
                .orElseThrow(() -> new AccessDeniedException("Payment request not found"));
        requirePending(request);
        request.setStatus(PaymentRequest.RequestStatus.DECLINED);
        request.setRespondedAt(Instant.now());
        PaymentRequest saved = paymentRequestRepository.save(request);
        notificationService.notify(
                request.getRequester(),
                Notification.NotificationType.PAYMENT_REQUEST,
                "Payment request declined",
                payer.getFullName() + " declined your request for " + request.getAmount() + " "
                        + request.getCurrency() + ".",
                "PAYMENT_REQUEST",
                saved.getId()
        );
        return toResponse(saved, payer);
    }

    @Transactional
    public PaymentRequestDto.Response cancel(Long id, User requester) {
        PaymentRequest request = paymentRequestRepository.findByIdAndRequesterId(id, requester.getId())
                .orElseThrow(() -> new AccessDeniedException("Payment request not found"));
        requirePending(request);
        request.setStatus(PaymentRequest.RequestStatus.CANCELLED);
        request.setRespondedAt(Instant.now());
        return toResponse(paymentRequestRepository.save(request), requester);
    }

    @Scheduled(fixedDelayString = "${app.scheduler.payment-request-delay-ms}")
    @Transactional
    public void expireStaleRequests() {
        List<PaymentRequest> expired = paymentRequestRepository.findByStatusAndExpiresAtBefore(
                PaymentRequest.RequestStatus.PENDING,
                Instant.now()
        );
        for (PaymentRequest request : expired) {
            request.setStatus(PaymentRequest.RequestStatus.EXPIRED);
            request.setRespondedAt(Instant.now());
            paymentRequestRepository.save(request);
            notificationService.notify(
                    request.getRequester(),
                    Notification.NotificationType.PAYMENT_REQUEST,
                    "Payment request expired",
                    "Your request for " + request.getAmount() + " " + request.getCurrency()
                            + " expired without a response.",
                    "PAYMENT_REQUEST",
                    request.getId()
            );
        }
    }

    private void requirePending(PaymentRequest request) {
        if (request.getStatus() != PaymentRequest.RequestStatus.PENDING) {
            throw new IllegalStateException(
                    "This request is already " + request.getStatus().name().toLowerCase()
            );
        }
        if (request.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("This request has expired");
        }
    }

    private User resolvePayer(String identifier) {
        String trimmed = identifier.trim();
        return userRepository.findByUsername(trimmed)
                .or(() -> userRepository.findByEmail(trimmed.toLowerCase()))
                .or(() -> accountRepository.findByAccountNumber(trimmed).map(Account::getUser))
                .orElseThrow(() -> new IllegalArgumentException("Payer not found"));
    }

    private PaymentRequestDto.Response toResponse(PaymentRequest request, User viewer) {
        boolean incoming = request.getPayer().getId().equals(viewer.getId());
        return new PaymentRequestDto.Response(
                request.getId(),
                incoming ? "INCOMING" : "OUTGOING",
                request.getRequester().getId(),
                request.getRequester().getFullName(),
                request.getRequesterAccount().getId(),
                bankMapper.maskAccountNumber(request.getRequesterAccount().getAccountNumber()),
                request.getPayer().getId(),
                request.getPayer().getFullName(),
                request.getAmount(),
                request.getCurrency(),
                request.getNote(),
                request.getStatus().name(),
                request.getExpiresAt(),
                request.getRespondedAt(),
                request.getTransaction() == null ? null : request.getTransaction().getId(),
                request.getCreatedAt()
        );
    }
}
