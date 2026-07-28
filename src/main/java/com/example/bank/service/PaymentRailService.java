package com.example.bank.service;

import com.example.bank.dto.ExternalTransferDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.ExternalTransfer;
import com.example.bank.entity.Notification;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.ExternalTransferRepository;
import com.example.bank.service.rail.PaymentRailAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class PaymentRailService {

    private final ExternalTransferRepository externalTransferRepository;
    private final AccountRepository accountRepository;
    private final TransactionService transactionService;
    private final KycGuardService kycGuardService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final List<PaymentRailAdapter> adapters;

    public PaymentRailService(
            ExternalTransferRepository externalTransferRepository,
            AccountRepository accountRepository,
            TransactionService transactionService,
            KycGuardService kycGuardService,
            NotificationService notificationService,
            AuditService auditService,
            List<PaymentRailAdapter> adapters
    ) {
        this.externalTransferRepository = externalTransferRepository;
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
        this.kycGuardService = kycGuardService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.adapters = adapters;
    }

    /**
     * Pulls money in from an external account. Nothing is credited here — the customer
     * balance only moves when the provider confirms settlement over the webhook.
     */
    @Transactional
    public ExternalTransferDto.Response initiateFunding(
            ExternalTransferDto.InitiateRequest request,
            User user
    ) {
        kycGuardService.requireApproved(user);
        Account account = ownedActiveAccount(request.accountId(), user);
        ExternalTransfer transfer = newTransfer(
                user,
                account,
                request,
                ExternalTransfer.TransferDirection.INBOUND
        );
        PaymentRailAdapter adapter = adapterFor(transfer.getRail());
        transfer.setProvider(adapter.name());
        ExternalTransfer saved = externalTransferRepository.save(transfer);

        PaymentRailAdapter.AdapterResult result = adapter.submitFunding(saved);
        saved.setProviderReference(result.providerReference());
        saved.setStatus(result.status());
        saved = externalTransferRepository.save(saved);

        auditService.log(user.getId(), "EXTERNAL_FUNDING_INITIATED", java.util.Map.of(
                "externalTransferId", saved.getId(),
                "amount", saved.getAmount(),
                "rail", saved.getRail().name()
        ));
        return toResponse(saved);
    }

    /**
     * Sends money out. The customer is debited immediately into the payout clearing
     * ledger so the funds cannot be spent twice; a failed or returned payout is reversed
     * back to the account when the provider says so.
     */
    @Transactional
    public ExternalTransferDto.Response initiatePayout(
            ExternalTransferDto.InitiateRequest request,
            User user,
            String idempotencyKey
    ) {
        kycGuardService.requireApproved(user);
        Account account = ownedActiveAccount(request.accountId(), user);
        ExternalTransfer transfer = newTransfer(
                user,
                account,
                request,
                ExternalTransfer.TransferDirection.OUTBOUND
        );
        PaymentRailAdapter adapter = adapterFor(transfer.getRail());
        transfer.setProvider(adapter.name());

        Transaction debit = transactionService.debitToSystem(
                account,
                request.amount(),
                user,
                idempotencyKey,
                "PAYOUT_CLEARING",
                Transaction.TransactionType.EXTERNAL_PAYOUT,
                "Outbound " + transfer.getRail().name() + " transfer to "
                        + maskCounterparty(transfer),
                true
        );
        transfer.setTransaction(debit);
        ExternalTransfer saved = externalTransferRepository.save(transfer);

        PaymentRailAdapter.AdapterResult result = adapter.submitPayout(saved);
        saved.setProviderReference(result.providerReference());
        saved.setStatus(result.status());
        saved = externalTransferRepository.save(saved);

        auditService.log(user.getId(), "EXTERNAL_PAYOUT_INITIATED", java.util.Map.of(
                "externalTransferId", saved.getId(),
                "transactionId", debit.getId(),
                "amount", saved.getAmount(),
                "rail", saved.getRail().name()
        ));
        return toResponse(saved);
    }

    public List<ExternalTransferDto.Response> list(User user) {
        return externalTransferRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ExternalTransferDto.Response get(Long id, User user) {
        return toResponse(externalTransferRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Transfer not found")));
    }

    public boolean verifySignature(String provider, String rawPayload, String signature) {
        return adapterByName(provider).verifySignature(rawPayload, signature);
    }

    /**
     * Applies a provider settlement callback. Replays of an already-terminal transfer are
     * ignored rather than rejected, because providers retry until they see a 2xx.
     */
    @Transactional
    public void handleWebhook(String provider, ExternalTransferDto.WebhookEvent event) {
        ExternalTransfer transfer = externalTransferRepository
                .findByProviderReference(event.providerReference())
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider reference"));
        if (!transfer.getProvider().equalsIgnoreCase(provider)) {
            throw new AccessDeniedException("Provider mismatch for this transfer");
        }
        if (isTerminal(transfer.getStatus())) {
            return;
        }

        switch (event.event().toLowerCase(Locale.ROOT)) {
            case "transfer.settled" -> settle(transfer);
            case "transfer.failed" -> fail(transfer, event.reason(), ExternalTransfer.TransferStatus.FAILED);
            case "transfer.returned" -> fail(transfer, event.reason(), ExternalTransfer.TransferStatus.RETURNED);
            default -> throw new IllegalArgumentException("Unsupported event: " + event.event());
        }
    }

    private void settle(ExternalTransfer transfer) {
        if (transfer.getDirection() == ExternalTransfer.TransferDirection.INBOUND) {
            Transaction credit = transactionService.creditFromSystem(
                    transfer.getAccount(),
                    transfer.getAmount(),
                    transfer.getUser(),
                    "rail-settle:" + transfer.getProviderReference(),
                    "FUNDING_CLEARING",
                    Transaction.TransactionType.EXTERNAL_FUNDING,
                    "Inbound " + transfer.getRail().name() + " funding from "
                            + maskCounterparty(transfer)
            );
            transfer.setTransaction(credit);
        }
        transfer.setStatus(ExternalTransfer.TransferStatus.SETTLED);
        transfer.setSettledAt(Instant.now());
        externalTransferRepository.save(transfer);

        notificationService.notify(
                transfer.getUser(),
                Notification.NotificationType.TRANSACTION,
                transfer.getDirection() == ExternalTransfer.TransferDirection.INBOUND
                        ? "Funds received"
                        : "Transfer sent",
                transfer.getAmount() + " " + transfer.getCurrency() + " "
                        + (transfer.getDirection() == ExternalTransfer.TransferDirection.INBOUND
                                ? "arrived in your account."
                                : "was delivered to the recipient."),
                "EXTERNAL_TRANSFER",
                transfer.getId()
        );
    }

    private void fail(
            ExternalTransfer transfer,
            String reason,
            ExternalTransfer.TransferStatus status
    ) {
        if (transfer.getDirection() == ExternalTransfer.TransferDirection.OUTBOUND
                && transfer.getTransaction() != null) {
            Transaction reversal = transactionService.creditFromSystem(
                    transfer.getAccount(),
                    transfer.getAmount(),
                    transfer.getUser(),
                    "rail-reverse:" + transfer.getProviderReference(),
                    "PAYOUT_CLEARING",
                    Transaction.TransactionType.REVERSAL,
                    "Reversal of failed payout " + transfer.getProviderReference()
            );
            transfer.setReversalTransaction(reversal);
        }
        transfer.setStatus(status);
        transfer.setFailureReason(reason == null ? "The provider rejected this transfer" : reason);
        externalTransferRepository.save(transfer);

        notificationService.notify(
                transfer.getUser(),
                Notification.NotificationType.TRANSACTION,
                "Transfer unsuccessful",
                transfer.getAmount() + " " + transfer.getCurrency() + " could not be sent: "
                        + transfer.getFailureReason()
                        + (transfer.getReversalTransaction() == null
                                ? ""
                                : " The amount has been returned to your account."),
                "EXTERNAL_TRANSFER",
                transfer.getId()
        );
    }

    private ExternalTransfer newTransfer(
            User user,
            Account account,
            ExternalTransferDto.InitiateRequest request,
            ExternalTransfer.TransferDirection direction
    ) {
        ExternalTransfer transfer = new ExternalTransfer();
        transfer.setUser(user);
        transfer.setAccount(account);
        transfer.setDirection(direction);
        transfer.setRail(parseRail(request.rail()));
        transfer.setAmount(request.amount());
        transfer.setCurrency(account.getCurrency());
        transfer.setCounterpartyName(request.counterpartyName());
        String reference = request.counterpartyReference().trim();
        transfer.setCounterpartyLastFour(
                reference.length() <= 4 ? reference : reference.substring(reference.length() - 4)
        );
        return transfer;
    }

    private boolean isTerminal(ExternalTransfer.TransferStatus status) {
        return status == ExternalTransfer.TransferStatus.SETTLED
                || status == ExternalTransfer.TransferStatus.FAILED
                || status == ExternalTransfer.TransferStatus.RETURNED;
    }

    private Account ownedActiveAccount(Long accountId, User user) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to use this account");
        }
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        return account;
    }

    private PaymentRailAdapter adapterFor(ExternalTransfer.PaymentRail rail) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(rail))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No provider is configured for " + rail));
    }

    private PaymentRailAdapter adapterByName(String provider) {
        return adapters.stream()
                .filter(adapter -> adapter.name().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + provider));
    }

    private ExternalTransfer.PaymentRail parseRail(String value) {
        try {
            return ExternalTransfer.PaymentRail.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported payment rail: " + value);
        }
    }

    private String maskCounterparty(ExternalTransfer transfer) {
        return "****" + transfer.getCounterpartyLastFour();
    }

    private ExternalTransferDto.Response toResponse(ExternalTransfer transfer) {
        return new ExternalTransferDto.Response(
                transfer.getId(),
                transfer.getAccount().getId(),
                transfer.getDirection().name(),
                transfer.getRail().name(),
                transfer.getProvider(),
                transfer.getProviderReference(),
                maskCounterparty(transfer),
                transfer.getCounterpartyName(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getCurrency(),
                transfer.getStatus().name(),
                transfer.getFailureReason(),
                transfer.getTransaction() == null ? null : transfer.getTransaction().getId(),
                transfer.getSettledAt(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }
}
