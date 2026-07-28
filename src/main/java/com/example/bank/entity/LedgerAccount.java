package com.example.bank.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LedgerAccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id", unique = true)
    private Account customerAccount;

    @Column(nullable = false)
    private boolean active = true;

    public enum LedgerAccountType {
        ASSET,
        LIABILITY,
        EQUITY,
        INCOME,
        EXPENSE
    }
}
