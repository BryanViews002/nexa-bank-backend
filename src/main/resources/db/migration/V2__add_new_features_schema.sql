-- Alter existing tables to match updated entities

ALTER TABLE accounts
    MODIFY COLUMN status ENUM('ACTIVE','FROZEN','CLOSED'),
    MODIFY COLUMN type   ENUM('CHECKING','SAVINGS','GOAL'),
    ADD COLUMN display_name              VARCHAR(80),
    ADD COLUMN daily_transfer_limit      DECIMAL(19,4) NOT NULL DEFAULT 10000.0000,
    ADD COLUMN daily_withdrawal_limit    DECIMAL(19,4) NOT NULL DEFAULT 2000.0000,
    ADD COLUMN online_transactions_enabled BIT NOT NULL DEFAULT 1;

ALTER TABLE transactions
    MODIFY COLUMN amount DECIMAL(19,4) NOT NULL,
    MODIFY COLUMN status ENUM('PENDING','COMPLETED','FAILED','REVERSED') NOT NULL,
    MODIFY COLUMN type   ENUM(
        'DEPOSIT','WITHDRAW','TRANSFER','BONUS',
        'SCHEDULED_PAYMENT','GOAL_CONTRIBUTION','GOAL_WITHDRAWAL',
        'CARD_PURCHASE','EXTERNAL_FUNDING','EXTERNAL_PAYOUT',
        'LOAN_DISBURSEMENT','LOAN_REPAYMENT','FX_EXCHANGE',
        'PAYMENT_REQUEST','REVERSAL'
    ) NOT NULL,
    ADD COLUMN fee                   DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN currency              VARCHAR(3)    NOT NULL DEFAULT 'USD',
    ADD COLUMN description           VARCHAR(500),
    ADD COLUMN category              VARCHAR(80),
    ADD COLUMN reference             VARCHAR(50)   UNIQUE,
    ADD COLUMN idempotency_key       VARCHAR(100),
    ADD COLUMN initiated_by_user_id  BIGINT,
    ADD COLUMN exchange_rate         DECIMAL(19,8),
    ADD COLUMN metadata              TEXT,
    ADD CONSTRAINT uk_transaction_user_operation_idempotency
        UNIQUE (initiated_by_user_id, type, idempotency_key);

-- Notifications

CREATE TABLE notifications (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    user_id              BIGINT NOT NULL,
    type                 ENUM(
                             'TRANSACTION','SECURITY','KYC','SCHEDULED_PAYMENT',
                             'SAVINGS_GOAL','BUDGET','PAYMENT_REQUEST','SUPPORT',
                             'CARD','LOAN','DISPUTE','SYSTEM'
                         ) NOT NULL,
    title                VARCHAR(160)  NOT NULL,
    message              VARCHAR(1000) NOT NULL,
    related_resource_type VARCHAR(60),
    related_resource_id  BIGINT,
    read_at              DATETIME(6),
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_preferences (
    id                          BIGINT NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT NOT NULL,
    in_app_enabled              BIT    NOT NULL DEFAULT 1,
    email_enabled               BIT    NOT NULL DEFAULT 1,
    security_alerts_enabled     BIT    NOT NULL DEFAULT 1,
    transaction_alerts_enabled  BIT    NOT NULL DEFAULT 1,
    budget_alerts_enabled       BIT    NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Beneficiaries

CREATE TABLE beneficiaries (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    user_id             BIGINT NOT NULL,
    name                VARCHAR(120) NOT NULL,
    account_number      VARCHAR(255) NOT NULL,
    recipient_username  VARCHAR(50),
    nickname            VARCHAR(80),
    active              BIT          NOT NULL DEFAULT 1,
    verified_at         DATETIME(6),
    last_used_at        DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_beneficiary_user_account UNIQUE (user_id, account_number),
    CONSTRAINT fk_beneficiaries_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Savings goals

CREATE TABLE savings_goals (
    id                              BIGINT NOT NULL AUTO_INCREMENT,
    user_id                         BIGINT NOT NULL,
    funding_account_id              BIGINT NOT NULL,
    goal_account_id                 BIGINT NOT NULL,
    name                            VARCHAR(120)  NOT NULL,
    description                     VARCHAR(500),
    target_amount                   DECIMAL(19,4) NOT NULL,
    target_date                     DATE,
    auto_contribution_amount        DECIMAL(19,4),
    auto_contribution_interval_days INT,
    next_auto_contribution          DATETIME(6),
    status                          ENUM('ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    version                         INT           NOT NULL DEFAULT 0,
    created_at                      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_savings_goals_goal_account UNIQUE (goal_account_id),
    CONSTRAINT fk_savings_goals_user           FOREIGN KEY (user_id)           REFERENCES users    (id),
    CONSTRAINT fk_savings_goals_funding_account FOREIGN KEY (funding_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_savings_goals_goal_account    FOREIGN KEY (goal_account_id)    REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Budgets

CREATE TABLE budgets (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    category        VARCHAR(80)   NOT NULL,
    monthly_limit   DECIMAL(19,4) NOT NULL,
    period_start    DATE          NOT NULL,
    alert_threshold DECIMAL(5,4)  NOT NULL DEFAULT 0.8000,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'USD',
    active          BIT           NOT NULL DEFAULT 1,
    last_alerted_at DATETIME(6),
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_budget_user_category_period UNIQUE (user_id, category, period_start),
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cards

CREATE TABLE cards (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    account_id            BIGINT        NOT NULL,
    card_number_hash      VARCHAR(64)   NOT NULL,
    last_four             VARCHAR(4)    NOT NULL,
    cvv_hash              VARCHAR(255)  NOT NULL,
    brand                 ENUM('VISA','MASTERCARD') NOT NULL,
    type                  ENUM('DEBIT','VIRTUAL')   NOT NULL,
    card_holder           VARCHAR(100)  NOT NULL,
    expiry_month          INT           NOT NULL,
    expiry_year           INT           NOT NULL,
    status                ENUM('ACTIVE','FROZEN','CANCELLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    daily_limit           DECIMAL(19,4) NOT NULL DEFAULT 2000.0000,
    per_transaction_limit DECIMAL(19,4) NOT NULL DEFAULT 1000.0000,
    contactless_enabled   BIT           NOT NULL DEFAULT 1,
    online_enabled        BIT           NOT NULL DEFAULT 1,
    international_enabled BIT           NOT NULL DEFAULT 0,
    frozen_at             DATETIME(6),
    version               INT           NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cards_card_number_hash UNIQUE (card_number_hash),
    CONSTRAINT fk_cards_user    FOREIGN KEY (user_id)    REFERENCES users    (id),
    CONSTRAINT fk_cards_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- External transfers (payment rails)

CREATE TABLE external_transfers (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    account_id            BIGINT        NOT NULL,
    direction             ENUM('INBOUND','OUTBOUND') NOT NULL,
    rail                  ENUM('ACH','SEPA','WIRE','CARD_FUNDING') NOT NULL,
    provider              VARCHAR(40)   NOT NULL,
    provider_reference    VARCHAR(100),
    counterparty_last_four VARCHAR(4),
    counterparty_name     VARCHAR(120),
    amount                DECIMAL(19,4) NOT NULL,
    fee                   DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency              VARCHAR(3)    NOT NULL,
    status                ENUM('PENDING','PROCESSING','SETTLED','FAILED','RETURNED') NOT NULL DEFAULT 'PENDING',
    failure_reason        VARCHAR(500),
    transaction_id        BIGINT,
    reversal_transaction_id BIGINT,
    settled_at            DATETIME(6),
    version               INT           NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_external_transfers_provider_reference UNIQUE (provider_reference),
    CONSTRAINT fk_external_transfers_user        FOREIGN KEY (user_id)                 REFERENCES users        (id),
    CONSTRAINT fk_external_transfers_account     FOREIGN KEY (account_id)              REFERENCES accounts     (id),
    CONSTRAINT fk_external_transfers_tx          FOREIGN KEY (transaction_id)          REFERENCES transactions (id),
    CONSTRAINT fk_external_transfers_reversal_tx FOREIGN KEY (reversal_transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Disputes

CREATE TABLE disputes (
    id                              BIGINT        NOT NULL AUTO_INCREMENT,
    case_reference                  VARCHAR(40)   NOT NULL,
    user_id                         BIGINT        NOT NULL,
    transaction_id                  BIGINT        NOT NULL,
    reason                          ENUM('UNAUTHORIZED','DUPLICATE_CHARGE','PRODUCT_NOT_RECEIVED',
                                         'PRODUCT_UNACCEPTABLE','INCORRECT_AMOUNT',
                                         'CANCELLED_RECURRING','OTHER') NOT NULL,
    description                     VARCHAR(2000) NOT NULL,
    amount                          DECIMAL(19,4) NOT NULL,
    currency                        VARCHAR(3)    NOT NULL,
    status                          ENUM('OPEN','UNDER_REVIEW','EVIDENCE_REQUESTED',
                                         'RESOLVED_CUSTOMER','RESOLVED_MERCHANT','WITHDRAWN')
                                    NOT NULL DEFAULT 'OPEN',
    provisional_credit_granted      BIT           NOT NULL DEFAULT 0,
    provisional_credit_transaction_id BIGINT,
    clawback_transaction_id         BIGINT,
    resolution_note                 VARCHAR(2000),
    resolved_by_admin_id            BIGINT,
    resolved_at                     DATETIME(6),
    version                         INT           NOT NULL DEFAULT 0,
    created_at                      DATETIME(6)   NOT NULL,
    updated_at                      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_disputes_case_reference UNIQUE (case_reference),
    CONSTRAINT fk_disputes_user             FOREIGN KEY (user_id)                         REFERENCES users        (id),
    CONSTRAINT fk_disputes_transaction      FOREIGN KEY (transaction_id)                  REFERENCES transactions (id),
    CONSTRAINT fk_disputes_provisional_tx   FOREIGN KEY (provisional_credit_transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_disputes_clawback_tx      FOREIGN KEY (clawback_transaction_id)         REFERENCES transactions (id),
    CONSTRAINT fk_disputes_resolved_by      FOREIGN KEY (resolved_by_admin_id)            REFERENCES users        (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Payment requests

CREATE TABLE payment_requests (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    requester_user_id    BIGINT        NOT NULL,
    requester_account_id BIGINT        NOT NULL,
    payer_user_id        BIGINT        NOT NULL,
    amount               DECIMAL(19,4) NOT NULL,
    currency             VARCHAR(3)    NOT NULL,
    note                 VARCHAR(500),
    status               ENUM('PENDING','ACCEPTED','DECLINED','CANCELLED','EXPIRED')
                         NOT NULL DEFAULT 'PENDING',
    expires_at           DATETIME(6)   NOT NULL,
    responded_at         DATETIME(6),
    transaction_id       BIGINT,
    created_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_requests_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_payment_requests_requester         FOREIGN KEY (requester_user_id)    REFERENCES users        (id),
    CONSTRAINT fk_payment_requests_requester_account FOREIGN KEY (requester_account_id) REFERENCES accounts     (id),
    CONSTRAINT fk_payment_requests_payer             FOREIGN KEY (payer_user_id)        REFERENCES users        (id),
    CONSTRAINT fk_payment_requests_transaction       FOREIGN KEY (transaction_id)       REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Support tickets and messages

CREATE TABLE support_tickets (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    assigned_admin_id BIGINT,
    subject           VARCHAR(160)  NOT NULL,
    category          ENUM('ACCOUNT','TRANSACTION','CARD','KYC','LOAN',
                           'DISPUTE','TECHNICAL','OTHER') NOT NULL,
    priority          ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
    status            ENUM('OPEN','IN_PROGRESS','WAITING_FOR_CUSTOMER',
                           'RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    resolution        VARCHAR(1000),
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_tickets_user  FOREIGN KEY (user_id)           REFERENCES users (id),
    CONSTRAINT fk_support_tickets_admin FOREIGN KEY (assigned_admin_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE support_messages (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    ticket_id      BIGINT        NOT NULL,
    author_user_id BIGINT        NOT NULL,
    body           VARCHAR(4000) NOT NULL,
    internal_note  BIT           NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_support_messages_ticket FOREIGN KEY (ticket_id)      REFERENCES support_tickets (id),
    CONSTRAINT fk_support_messages_author FOREIGN KEY (author_user_id) REFERENCES users           (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Idempotency records

CREATE TABLE idempotency_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    operation_name  VARCHAR(80)  NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    resource_type   VARCHAR(80)  NOT NULL,
    resource_id     BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_user_operation_key UNIQUE (user_id, operation_name, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Ledger

CREATE TABLE ledger_accounts (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(100) NOT NULL,
    name                VARCHAR(120) NOT NULL,
    type                ENUM('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE') NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    customer_account_id BIGINT,
    active              BIT          NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_accounts_code             UNIQUE (code),
    CONSTRAINT uk_ledger_accounts_customer_account UNIQUE (customer_account_id),
    CONSTRAINT fk_ledger_accounts_customer_account FOREIGN KEY (customer_account_id) REFERENCES accounts (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_journals (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    reference   VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ledger_journals_reference UNIQUE (reference)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ledger_postings (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    journal_id        BIGINT        NOT NULL,
    ledger_account_id BIGINT        NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ledger_postings_journal FOREIGN KEY (journal_id)        REFERENCES ledger_journals (id),
    CONSTRAINT fk_ledger_postings_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed system ledger accounts required by PaymentRailService and DisputeService

INSERT INTO ledger_accounts (code, name, type, currency) VALUES
    ('PAYOUT_CLEARING',   'Payout Clearing',   'LIABILITY', 'USD'),
    ('FUNDING_CLEARING',  'Funding Clearing',  'LIABILITY', 'USD'),
    ('DISPUTE_SUSPENSE',  'Dispute Suspense',  'LIABILITY', 'USD'),
    ('CARD_SETTLEMENT',   'Card Settlement',   'LIABILITY', 'USD');
