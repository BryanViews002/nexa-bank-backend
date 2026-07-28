package com.example.bank.service;

import com.example.bank.dto.DisputeDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Dispute;
import com.example.bank.entity.Notification;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.DisputeRepository;
import com.example.bank.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DisputeService {

    /** Card-network chargeback windows are measured in months; 120 days is the working limit here. */
    private static final int DISPUTE_WINDOW_DAYS = 120;

    private static final List<Dispute.DisputeStatus> INACTIVE_STATUSES = List.of(
            Dispute.DisputeStatus.WITHDRAWN,
            Dispute.DisputeStatus.RESOLVED_MERCHANT
    );

    private final DisputeRepository disputeRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public DisputeService(
            DisputeRepository disputeRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            TransactionService transactionService,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.disputeRepository = disputeRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Transactional
    public DisputeDto.Response file(DisputeDto.CreateRequest request, User user) {
        Transaction transaction = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        Account debited = transaction.getFromAccount();
        if (debited == null || !debited.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only dispute a charge against your own account");
        }
        if (transaction.getStatus() != Transaction.TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Only completed transactions can be disputed");
        }
        if (transaction.getDate().isBefore(LocalDateTime.now().minusDays(DISPUTE_WINDOW_DAYS))) {
            throw new IllegalStateException(
                    "This transaction is outside the " + DISPUTE_WINDOW_DAYS + "-day dispute window"
            );
        }
        if (disputeRepository.existsByTransactionIdAndStatusNotIn(transaction.getId(), INACTIVE_STATUSES)) {
            throw new IllegalStateException("A dispute for this transaction already exists");
        }

        Dispute dispute = new Dispute();
        dispute.setCaseReference("DSP-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        dispute.setUser(user);
        dispute.setTransaction(transaction);
        dispute.setReason(parseReason(request.reason()));
        dispute.setDescription(request.description().trim());
        dispute.setAmount(transaction.getAmount());
        dispute.setCurrency(transaction.getCurrency());
        Dispute saved = disputeRepository.save(dispute);

        auditService.log(user.getId(), "DISPUTE_FILED", Map.of(
                "disputeId", saved.getId(),
                "caseReference", saved.getCaseReference(),
                "transactionId", transaction.getId()
        ));
        notificationService.notify(
                user,
                Notification.NotificationType.DISPUTE,
                "Dispute received",
                "We opened case " + saved.getCaseReference() + " for " + saved.getAmount() + " "
                        + saved.getCurrency() + " and will be in touch.",
                "DISPUTE",
                saved.getId()
        );
        return toResponse(saved);
    }

    public List<DisputeDto.Response> listMine(User user) {
        return disputeRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public DisputeDto.Response get(Long id, User user) {
        return toResponse(disputeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Dispute not found")));
    }

    @Transactional
    public DisputeDto.Response withdraw(Long id, User user) {
        Dispute dispute = disputeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Dispute not found"));
        requireOpen(dispute);
        if (dispute.isProvisionalCreditGranted()) {
            throw new IllegalStateException(
                    "This case carries a provisional credit and must be resolved by support"
            );
        }
        dispute.setStatus(Dispute.DisputeStatus.WITHDRAWN);
        dispute.setResolvedAt(Instant.now());
        return toResponse(disputeRepository.save(dispute));
    }

    public Page<DisputeDto.Response> listForAdmin(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<Dispute> disputes = status == null || status.isBlank()
                ? disputeRepository.findAllByOrderByCreatedAtDesc(pageable)
                : disputeRepository.findByStatusOrderByCreatedAtDesc(
                        parseStatus(status),
                        pageable
                );
        return disputes.map(this::toResponse);
    }

    public DisputeDto.Response getForAdmin(Long id) {
        return toResponse(byId(id));
    }

    @Transactional
    public DisputeDto.Response updateAsAdmin(Long id, DisputeDto.AdminUpdateRequest request) {
        Dispute dispute = byId(id);
        requireOpen(dispute);
        if (request.status() != null) {
            Dispute.DisputeStatus status = parseStatus(request.status());
            if (status == Dispute.DisputeStatus.RESOLVED_CUSTOMER
                    || status == Dispute.DisputeStatus.RESOLVED_MERCHANT) {
                throw new IllegalArgumentException("Use the resolve endpoint to close a case");
            }
            dispute.setStatus(status);
        }
        if (request.note() != null) {
            dispute.setResolutionNote(request.note());
        }
        Dispute saved = disputeRepository.save(dispute);
        notificationService.notify(
                saved.getUser(),
                Notification.NotificationType.DISPUTE,
                "Dispute update",
                "Case " + saved.getCaseReference() + " is now "
                        + saved.getStatus().name().replace('_', ' ').toLowerCase(Locale.ROOT) + ".",
                "DISPUTE",
                saved.getId()
        );
        return toResponse(saved);
    }

    /**
     * Fronts the disputed amount to the customer while the case is investigated. The money
     * comes from the dispute suspense ledger, and is clawed back if the case is later
     * resolved in the merchant's favour.
     */
    @Transactional
    public DisputeDto.Response grantProvisionalCredit(Long id, User admin) {
        Dispute dispute = byId(id);
        requireOpen(dispute);
        if (dispute.isProvisionalCreditGranted()) {
            throw new IllegalStateException("A provisional credit has already been granted");
        }
        Account account = accountRepository.findById(dispute.getTransaction().getFromAccount().getId())
                .orElseThrow(() -> new IllegalStateException("The disputed account no longer exists"));

        Transaction credit = transactionService.creditFromSystem(
                account,
                dispute.getAmount(),
                dispute.getUser(),
                "dispute-credit:" + dispute.getId(),
                "DISPUTE_SUSPENSE",
                Transaction.TransactionType.REVERSAL,
                "Provisional credit for dispute " + dispute.getCaseReference()
        );
        dispute.setProvisionalCreditGranted(true);
        dispute.setProvisionalCreditTransaction(credit);
        dispute.setStatus(Dispute.DisputeStatus.UNDER_REVIEW);
        Dispute saved = disputeRepository.save(dispute);

        auditService.log(admin.getId(), "DISPUTE_PROVISIONAL_CREDIT", Map.of(
                "disputeId", saved.getId(),
                "transactionId", credit.getId(),
                "amount", saved.getAmount()
        ));
        notificationService.notify(
                saved.getUser(),
                Notification.NotificationType.DISPUTE,
                "Provisional credit applied",
                dispute.getAmount() + " " + dispute.getCurrency() + " was credited to your account "
                        + "while we investigate case " + saved.getCaseReference() + ".",
                "DISPUTE",
                saved.getId()
        );
        return toResponse(saved);
    }

    /**
     * Closes a case. In the customer's favour the credit is made permanent (issued now if it
     * was not fronted earlier); in the merchant's favour any provisional credit is reclaimed.
     * A reclaim fails loudly if the account can no longer cover it, so the shortfall is
     * visible rather than silently absorbed.
     */
    @Transactional
    public DisputeDto.Response resolve(Long id, DisputeDto.ResolveRequest request, User admin) {
        Dispute dispute = byId(id);
        requireOpen(dispute);
        Account account = accountRepository.findById(dispute.getTransaction().getFromAccount().getId())
                .orElseThrow(() -> new IllegalStateException("The disputed account no longer exists"));

        if (Boolean.TRUE.equals(request.inFavourOfCustomer())) {
            if (!dispute.isProvisionalCreditGranted()) {
                Transaction refund = transactionService.creditFromSystem(
                        account,
                        dispute.getAmount(),
                        dispute.getUser(),
                        "dispute-refund:" + dispute.getId(),
                        "DISPUTE_SUSPENSE",
                        Transaction.TransactionType.REVERSAL,
                        "Dispute refund for " + dispute.getCaseReference()
                );
                dispute.setProvisionalCreditGranted(true);
                dispute.setProvisionalCreditTransaction(refund);
            }
            dispute.setStatus(Dispute.DisputeStatus.RESOLVED_CUSTOMER);
        } else {
            if (dispute.isProvisionalCreditGranted()) {
                Transaction clawback = transactionService.debitToSystem(
                        account,
                        dispute.getAmount(),
                        dispute.getUser(),
                        "dispute-clawback:" + dispute.getId(),
                        "DISPUTE_SUSPENSE",
                        Transaction.TransactionType.REVERSAL,
                        "Provisional credit reclaimed for " + dispute.getCaseReference(),
                        false,
                        false
                );
                dispute.setClawbackTransaction(clawback);
            }
            dispute.setStatus(Dispute.DisputeStatus.RESOLVED_MERCHANT);
        }

        dispute.setResolutionNote(request.resolutionNote().trim());
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(Instant.now());
        Dispute saved = disputeRepository.save(dispute);

        auditService.log(admin.getId(), "DISPUTE_RESOLVED", Map.of(
                "disputeId", saved.getId(),
                "outcome", saved.getStatus().name()
        ));
        notificationService.notify(
                saved.getUser(),
                Notification.NotificationType.DISPUTE,
                "Dispute resolved",
                "Case " + saved.getCaseReference() + " was resolved "
                        + (saved.getStatus() == Dispute.DisputeStatus.RESOLVED_CUSTOMER
                                ? "in your favour."
                                : "in the merchant's favour.")
                        + " " + saved.getResolutionNote(),
                "DISPUTE",
                saved.getId()
        );
        return toResponse(saved);
    }

    private void requireOpen(Dispute dispute) {
        if (dispute.getResolvedAt() != null
                || dispute.getStatus() == Dispute.DisputeStatus.RESOLVED_CUSTOMER
                || dispute.getStatus() == Dispute.DisputeStatus.RESOLVED_MERCHANT
                || dispute.getStatus() == Dispute.DisputeStatus.WITHDRAWN) {
            throw new IllegalStateException("This dispute is already closed");
        }
    }

    private Dispute byId(Long id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));
    }

    private Dispute.DisputeReason parseReason(String value) {
        try {
            return Dispute.DisputeReason.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported dispute reason: " + value);
        }
    }

    private Dispute.DisputeStatus parseStatus(String value) {
        try {
            return Dispute.DisputeStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported dispute status: " + value);
        }
    }

    private DisputeDto.Response toResponse(Dispute dispute) {
        return new DisputeDto.Response(
                dispute.getId(),
                dispute.getCaseReference(),
                dispute.getUser().getId(),
                dispute.getUser().getFullName(),
                dispute.getTransaction().getId(),
                dispute.getTransaction().getReference(),
                dispute.getReason().name(),
                dispute.getDescription(),
                dispute.getAmount(),
                dispute.getCurrency(),
                dispute.getStatus().name(),
                dispute.isProvisionalCreditGranted(),
                dispute.getProvisionalCreditTransaction() == null
                        ? null
                        : dispute.getProvisionalCreditTransaction().getId(),
                dispute.getClawbackTransaction() == null ? null : dispute.getClawbackTransaction().getId(),
                dispute.getResolutionNote(),
                dispute.getResolvedAt(),
                dispute.getCreatedAt(),
                dispute.getUpdatedAt()
        );
    }
}
