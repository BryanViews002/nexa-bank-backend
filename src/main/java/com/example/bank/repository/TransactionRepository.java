package com.example.bank.repository;

import com.example.bank.entity.Account;
import com.example.bank.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountOrToAccount(Account fromAccount, Account toAccount);

    List<Transaction> findByFromAccountInOrToAccountIn(List<Account> fromAccounts, List<Account> toAccounts);
}