CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    description VARCHAR(500),
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE users (
    enabled BIT NOT NULL,
    failed_login_count INTEGER,
    locked BIT NOT NULL,
    created_at DATETIME(6),
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    full_name VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE accounts (
    balance FLOAT(53) NOT NULL,
    version INTEGER NOT NULL,
    created_at DATETIME(6),
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    currency VARCHAR(255),
    status ENUM ('ACTIVE', 'FROZEN'),
    type ENUM ('CHECKING', 'SAVINGS'),
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    timestamp DATETIME(6) NOT NULL,
    user_id BIGINT,
    action VARCHAR(255) NOT NULL,
    details TEXT,
    ip_address VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE kyc_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uploaded_at DATETIME(6),
    user_id BIGINT NOT NULL,
    content_type VARCHAR(255),
    filename VARCHAR(255),
    path VARCHAR(255),
    status ENUM ('APPROVED', 'PENDING', 'REJECTED'),
    PRIMARY KEY (id),
    CONSTRAINT fk_kyc_documents_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE otps (
    used BIT NOT NULL,
    created_at DATETIME(6),
    expires_at DATETIME(6),
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code VARCHAR(255),
    purpose ENUM ('LOGIN', 'PASSWORD_RESET'),
    PRIMARY KEY (id),
    CONSTRAINT fk_otps_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE scheduled_payments (
    amount DECIMAL(18, 2),
    enabled BIT NOT NULL,
    interval_days INTEGER NOT NULL,
    account_from_id BIGINT NOT NULL,
    created_at DATETIME(6),
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_run DATETIME(6),
    next_run DATETIME(6),
    account_to VARCHAR(255),
    currency VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT fk_scheduled_payments_account
        FOREIGN KEY (account_from_id) REFERENCES accounts (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transactions (
    amount FLOAT(53) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    from_account_id BIGINT,
    id BIGINT NOT NULL AUTO_INCREMENT,
    to_account_id BIGINT,
    tx_uuid VARCHAR(36) NOT NULL,
    to_external_account VARCHAR(255),
    status ENUM ('COMPLETED', 'FAILED', 'PENDING') NOT NULL,
    type ENUM ('BONUS', 'DEPOSIT', 'TRANSFER', 'WITHDRAW') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transactions_tx_uuid UNIQUE (tx_uuid),
    CONSTRAINT fk_transactions_from_account
        FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_to_account
        FOREIGN KEY (to_account_id) REFERENCES accounts (id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

INSERT INTO roles (name, description)
VALUES
    ('ROLE_USER', 'Standard banking user'),
    ('ROLE_ADMIN', 'Bank administrator');
