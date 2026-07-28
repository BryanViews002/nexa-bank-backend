package com.example.bank.service;

import com.example.bank.dto.CardDto;
import com.example.bank.entity.Account;
import com.example.bank.entity.Card;
import com.example.bank.entity.Notification;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.CardRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.util.CardNumberGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class CardService {

    private static final int CARD_VALIDITY_YEARS = 4;

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final KycGuardService kycGuardService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final String pepper;

    public CardService(
            CardRepository cardRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            TransactionService transactionService,
            KycGuardService kycGuardService,
            NotificationService notificationService,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.card-pepper}") String pepper
    ) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.kycGuardService = kycGuardService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.pepper = pepper;
    }

    @Transactional
    public CardDto.IssuedResponse issue(CardDto.IssueRequest request, User user) {
        kycGuardService.requireApproved(user);
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to issue a card on this account");
        }
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cards can only be issued on active accounts");
        }
        if (account.getType() == Account.AccountType.GOAL) {
            throw new IllegalArgumentException("Cards cannot be issued against a savings goal account");
        }

        Card.CardBrand brand = parse(request.brand(), Card.CardBrand.class, Card.CardBrand.VISA);
        Card.CardType type = parse(request.type(), Card.CardType.class, Card.CardType.DEBIT);

        String pan = uniqueCardNumber(brand);
        String cvv = CardNumberGenerator.generateCvv();
        YearMonth expiry = YearMonth.now().plusYears(CARD_VALIDITY_YEARS);

        Card card = new Card();
        card.setUser(user);
        card.setAccount(account);
        card.setCardNumberHash(hash(pan));
        card.setLastFour(pan.substring(pan.length() - 4));
        card.setCvvHash(passwordEncoder.encode(cvv));
        card.setBrand(brand);
        card.setType(type);
        card.setCardHolder(user.getFullName() == null ? user.getUsername() : user.getFullName());
        card.setExpiryMonth(expiry.getMonthValue());
        card.setExpiryYear(expiry.getYear());
        if (request.dailyLimit() != null) {
            card.setDailyLimit(request.dailyLimit());
        }
        if (request.perTransactionLimit() != null) {
            card.setPerTransactionLimit(request.perTransactionLimit());
        }
        Card saved = cardRepository.save(card);

        auditService.log(user.getId(), "CARD_ISSUED", java.util.Map.of(
                "cardId", saved.getId(),
                "accountId", account.getId(),
                "lastFour", saved.getLastFour()
        ));
        notificationService.notify(
                user,
                Notification.NotificationType.CARD,
                "New card issued",
                brand.name() + " " + type.name().toLowerCase(Locale.ROOT) + " card ending "
                        + saved.getLastFour() + " is ready to use.",
                "CARD",
                saved.getId()
        );
        return new CardDto.IssuedResponse(toResponse(saved), pan, cvv);
    }

    public List<CardDto.Response> list(User user) {
        return cardRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CardDto.Response get(Long id, User user) {
        return toResponse(owned(id, user));
    }

    @Transactional
    public CardDto.Response updateControls(Long id, CardDto.ControlRequest request, User user) {
        Card card = owned(id, user);
        requireNotCancelled(card);
        if (request.dailyLimit() != null) {
            card.setDailyLimit(request.dailyLimit());
        }
        if (request.perTransactionLimit() != null) {
            card.setPerTransactionLimit(request.perTransactionLimit());
        }
        if (request.contactlessEnabled() != null) {
            card.setContactlessEnabled(request.contactlessEnabled());
        }
        if (request.onlineEnabled() != null) {
            card.setOnlineEnabled(request.onlineEnabled());
        }
        if (request.internationalEnabled() != null) {
            card.setInternationalEnabled(request.internationalEnabled());
        }
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public CardDto.Response freeze(Long id, User user) {
        Card card = owned(id, user);
        requireNotCancelled(card);
        card.setStatus(Card.CardStatus.FROZEN);
        card.setFrozenAt(java.time.Instant.now());
        Card saved = cardRepository.save(card);
        notificationService.notify(
                user,
                Notification.NotificationType.CARD,
                "Card frozen",
                "Card ending " + saved.getLastFour() + " was frozen and cannot be used.",
                "CARD",
                saved.getId()
        );
        return toResponse(saved);
    }

    @Transactional
    public CardDto.Response unfreeze(Long id, User user) {
        Card card = owned(id, user);
        if (card.getStatus() != Card.CardStatus.FROZEN) {
            throw new IllegalStateException("Only a frozen card can be unfrozen");
        }
        if (card.isExpired()) {
            throw new IllegalStateException("This card has expired");
        }
        card.setStatus(Card.CardStatus.ACTIVE);
        card.setFrozenAt(null);
        return toResponse(cardRepository.save(card));
    }

    @Transactional
    public CardDto.Response cancel(Long id, User user) {
        Card card = owned(id, user);
        card.setStatus(Card.CardStatus.CANCELLED);
        Card saved = cardRepository.save(card);
        auditService.log(user.getId(), "CARD_CANCELLED", java.util.Map.of("cardId", saved.getId()));
        return toResponse(saved);
    }

    /**
     * Simulates a card authorisation. The linked account is debited to the card settlement
     * ledger, so the double-entry books stay balanced exactly as they would for a real
     * network settlement.
     */
    @Transactional
    public CardDto.Response purchase(
            Long id,
            CardDto.PurchaseRequest request,
            User user,
            String idempotencyKey
    ) {
        Card card = owned(id, user);
        authorize(card, request);

        Account account = accountRepository.findById(card.getAccount().getId()).orElseThrow();
        Transaction transaction = transactionService.debitToSystem(
                account,
                request.amount(),
                user,
                idempotencyKey == null ? "card:" + card.getId() + ":" + request.merchant() : idempotencyKey,
                "CARD_SETTLEMENT",
                Transaction.TransactionType.CARD_PURCHASE,
                request.merchant().trim(),
                true
        );
        transaction.setCategory(
                request.category() == null || request.category().isBlank()
                        ? "SHOPPING"
                        : request.category().trim().toUpperCase(Locale.ROOT)
        );
        transaction.setMetadata("{\"cardId\":" + card.getId() + ",\"lastFour\":\"" + card.getLastFour() + "\"}");
        transactionRepository.save(transaction);

        return toResponse(card);
    }

    private void authorize(Card card, CardDto.PurchaseRequest request) {
        if (card.getStatus() == Card.CardStatus.FROZEN) {
            throw new IllegalStateException("This card is frozen");
        }
        if (card.getStatus() != Card.CardStatus.ACTIVE) {
            throw new IllegalStateException("This card is " + card.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (card.isExpired()) {
            card.setStatus(Card.CardStatus.EXPIRED);
            cardRepository.save(card);
            throw new IllegalStateException("This card has expired");
        }
        if (request.amount().compareTo(card.getPerTransactionLimit()) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the per-transaction limit of " + card.getPerTransactionLimit()
            );
        }
        if (Boolean.TRUE.equals(request.online()) && !card.isOnlineEnabled()) {
            throw new IllegalStateException("Online payments are disabled for this card");
        }
        if (Boolean.TRUE.equals(request.international()) && !card.isInternationalEnabled()) {
            throw new IllegalStateException("International payments are disabled for this card");
        }
        BigDecimal spentToday = dailySpend(card);
        if (spentToday.add(request.amount()).compareTo(card.getDailyLimit()) > 0) {
            throw new IllegalArgumentException(
                    "Daily card limit exceeded. Limit: " + card.getDailyLimit() + ", used: " + spentToday
            );
        }
    }

    private BigDecimal dailySpend(Card card) {
        return transactionRepository.sumCompletedOutgoingSince(
                card.getAccount(),
                List.of(Transaction.TransactionType.CARD_PURCHASE),
                LocalDate.now().atStartOfDay()
        );
    }

    private String uniqueCardNumber(Card.CardBrand brand) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = CardNumberGenerator.generate(brand);
            if (!cardRepository.existsByCardNumberHash(hash(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique card number");
    }

    private String hash(String pan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((pan + pepper).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Card owned(Long id, User user) {
        return cardRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Card not found"));
    }

    private void requireNotCancelled(Card card) {
        if (card.getStatus() == Card.CardStatus.CANCELLED) {
            throw new IllegalStateException("This card has been cancelled");
        }
    }

    private CardDto.Response toResponse(Card card) {
        return new CardDto.Response(
                card.getId(),
                card.getAccount().getId(),
                "**** **** **** " + card.getLastFour(),
                card.getLastFour(),
                card.getBrand().name(),
                card.getType().name(),
                card.getCardHolder(),
                String.format("%02d/%d", card.getExpiryMonth(), card.getExpiryYear() % 100),
                card.getStatus().name(),
                card.getDailyLimit(),
                card.getPerTransactionLimit(),
                dailySpend(card),
                card.isContactlessEnabled(),
                card.isOnlineEnabled(),
                card.isInternationalEnabled(),
                card.getCreatedAt()
        );
    }

    private <E extends Enum<E>> E parse(String value, Class<E> type, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid value for " + type.getSimpleName() + ": " + value);
        }
    }
}
