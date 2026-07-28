package com.example.bank.service;

import com.example.bank.dto.BankMapper;
import com.example.bank.dto.TransactionResponse;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class StatementService {

    private final TransactionService transactionService;
    private final BankMapper bankMapper;

    public StatementService(TransactionService transactionService, BankMapper bankMapper) {
        this.transactionService = transactionService;
        this.bankMapper = bankMapper;
    }

    public Page<TransactionResponse> statement(
            Long accountId,
            User user,
            Transaction.TransactionType type,
            Transaction.TransactionStatus status,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String query,
            int page,
            int size
    ) {
        return transactionService.getHistory(
                accountId,
                user,
                type,
                status,
                category,
                startDate,
                endDate,
                minAmount,
                maxAmount,
                query,
                page,
                size
        ).map(bankMapper::toTransactionResponse);
    }

    public byte[] csv(Long accountId, User user, LocalDateTime startDate, LocalDateTime endDate) {
        StringBuilder csv = new StringBuilder(
                "Reference,Date,Type,Status,Amount,Fee,Currency,Category,Description,From,To\n"
        );
        statement(
                accountId,
                user,
                null,
                null,
                null,
                startDate,
                endDate,
                null,
                null,
                null,
                0,
                1000
        ).forEach(transaction -> csv
                .append(escape(transaction.reference())).append(',')
                .append(escape(String.valueOf(transaction.createdAt()))).append(',')
                .append(escape(transaction.type())).append(',')
                .append(escape(transaction.status())).append(',')
                .append(transaction.amount()).append(',')
                .append(transaction.fee()).append(',')
                .append(escape(transaction.currency())).append(',')
                .append(escape(transaction.category())).append(',')
                .append(escape(transaction.description())).append(',')
                .append(escape(transaction.fromAccountNumber())).append(',')
                .append(escape(transaction.toAccountNumber())).append('\n')
        );
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
