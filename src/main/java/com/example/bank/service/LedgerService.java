package com.example.bank.service;

import com.example.bank.entity.Account;
import com.example.bank.entity.LedgerAccount;
import com.example.bank.entity.LedgerJournal;
import com.example.bank.entity.LedgerPosting;
import com.example.bank.entity.Transaction;
import com.example.bank.repository.LedgerAccountRepository;
import com.example.bank.repository.LedgerJournalRepository;
import com.example.bank.repository.LedgerPostingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LedgerService {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerJournalRepository ledgerJournalRepository;
    private final LedgerPostingRepository ledgerPostingRepository;

    public LedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerJournalRepository ledgerJournalRepository,
            LedgerPostingRepository ledgerPostingRepository
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerJournalRepository = ledgerJournalRepository;
        this.ledgerPostingRepository = ledgerPostingRepository;
    }

    @Transactional
    public LedgerAccount ensureCustomerAccount(Account account) {
        return ledgerAccountRepository.findByCustomerAccountId(account.getId())
                .orElseGet(() -> {
                    LedgerAccount ledgerAccount = new LedgerAccount();
                    ledgerAccount.setCode("CUSTOMER:" + account.getAccountNumber());
                    ledgerAccount.setName("Customer account " + account.getAccountNumber());
                    ledgerAccount.setType(LedgerAccount.LedgerAccountType.LIABILITY);
                    ledgerAccount.setCurrency(account.getCurrency());
                    ledgerAccount.setCustomerAccount(account);
                    return ledgerAccountRepository.save(ledgerAccount);
                });
    }

    @Transactional
    public LedgerAccount systemAccount(
            String purpose,
            String currency,
            LedgerAccount.LedgerAccountType type
    ) {
        String normalizedCurrency = normalizeCurrency(currency);
        String code = "SYSTEM:" + purpose.toUpperCase(Locale.ROOT) + ":" + normalizedCurrency;
        return ledgerAccountRepository.findByCode(code)
                .orElseGet(() -> {
                    LedgerAccount account = new LedgerAccount();
                    account.setCode(code);
                    account.setName(purpose.replace('_', ' ') + " " + normalizedCurrency);
                    account.setType(type);
                    account.setCurrency(normalizedCurrency);
                    return ledgerAccountRepository.save(account);
                });
    }

    @Transactional
    public LedgerJournal post(
            Transaction transaction,
            String eventType,
            String description,
            List<PostingRequest> requests
    ) {
        if (requests == null || requests.size() < 2) {
            throw new IllegalArgumentException("A journal requires at least two postings");
        }

        validateBalanced(requests);

        LedgerJournal journal = new LedgerJournal();
        journal.setTransaction(transaction);
        journal.setEventType(eventType);
        journal.setDescription(description);
        journal = ledgerJournalRepository.save(journal);

        List<LedgerPosting> postings = new ArrayList<>();
        for (PostingRequest request : requests) {
            LedgerPosting posting = new LedgerPosting();
            posting.setJournal(journal);
            posting.setLedgerAccount(request.ledgerAccount());
            posting.setDirection(request.direction());
            posting.setAmount(request.amount());
            posting.setCurrency(normalizeCurrency(request.currency()));
            postings.add(posting);
        }
        ledgerPostingRepository.saveAll(postings);
        return journal;
    }

    private void validateBalanced(List<PostingRequest> requests) {
        Map<String, BigDecimal> debits = new HashMap<>();
        Map<String, BigDecimal> credits = new HashMap<>();

        for (PostingRequest request : requests) {
            if (request.amount() == null || request.amount().signum() <= 0) {
                throw new IllegalArgumentException("Ledger posting amount must be positive");
            }
            String currency = normalizeCurrency(request.currency());
            Map<String, BigDecimal> target = request.direction() == LedgerPosting.PostingDirection.DEBIT
                    ? debits
                    : credits;
            target.merge(currency, request.amount(), BigDecimal::add);
        }

        for (String currency : unionCurrencies(debits, credits)) {
            BigDecimal debit = debits.getOrDefault(currency, BigDecimal.ZERO);
            BigDecimal credit = credits.getOrDefault(currency, BigDecimal.ZERO);
            if (debit.compareTo(credit) != 0) {
                throw new IllegalStateException(
                        "Unbalanced journal for " + currency + ": debits=" + debit + ", credits=" + credit
                );
            }
        }
    }

    private List<String> unionCurrencies(
            Map<String, BigDecimal> debits,
            Map<String, BigDecimal> credits
    ) {
        List<String> currencies = new ArrayList<>(debits.keySet());
        credits.keySet().stream().filter(currency -> !currencies.contains(currency)).forEach(currencies::add);
        return currencies;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    public record PostingRequest(
            LedgerAccount ledgerAccount,
            LedgerPosting.PostingDirection direction,
            BigDecimal amount,
            String currency
    ) {
    }
}
