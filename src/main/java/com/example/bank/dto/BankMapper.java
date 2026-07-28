package com.example.bank.dto;

import com.example.bank.entity.Account;
import com.example.bank.entity.KycDocument;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import org.springframework.stereotype.Component;

@Component
public class BankMapper {

    public AccountResponse toAccountResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                maskAccountNumber(account.getAccountNumber()),
                account.getDisplayName(),
                account.getType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getDailyTransferLimit(),
                account.getDailyWithdrawalLimit(),
                account.isOnlineTransactionsEnabled(),
                account.getCreatedAt()
        );
    }

    public TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getTxUuid(),
                transaction.getFromAccount() == null ? null : transaction.getFromAccount().getId(),
                transaction.getFromAccount() == null
                        ? null
                        : maskAccountNumber(transaction.getFromAccount().getAccountNumber()),
                transaction.getToAccount() == null ? null : transaction.getToAccount().getId(),
                transaction.getToAccount() == null
                        ? null
                        : maskAccountNumber(transaction.getToAccount().getAccountNumber()),
                maskAccountNumber(transaction.getToExternalAccount()),
                transaction.getAmount(),
                transaction.getFee(),
                transaction.getCurrency(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getDescription(),
                transaction.getCategory(),
                transaction.getExchangeRate(),
                transaction.getDate()
        );
    }

    public UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getRole().getName(),
                user.getKycStatus().name(),
                user.isEnabled(),
                user.isLocked(),
                user.getCreatedAt()
        );
    }

    public KycDocumentResponse toKycDocumentResponse(KycDocument document) {
        return new KycDocumentResponse(
                document.getId(),
                document.getUser().getId(),
                document.getFilename(),
                document.getContentType(),
                document.getStatus().name(),
                document.getRejectionReason(),
                document.getUploadedAt(),
                document.getReviewedAt()
        );
    }

    public String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
