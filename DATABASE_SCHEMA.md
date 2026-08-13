# Nexa Bank Database Schema

The application uses MySQL 8 and expects a database named `bank_app`.
There are eight application tables. Flyway also creates its own
`flyway_schema_history` table when the application starts.

The schema was reconstructed from all JPA entities, repositories, services,
configuration, Git history, and Hibernate's generated MySQL DDL.

## Restore a Fresh Database

Create the empty database:

```sq
CREATE DATABASE bank_app
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

Then start the backend. Flyway will automatically run:

```text
src/main/resources/db/migration/V1__create_bank_schema.sql
```

Alternatively, select `bank_app` in MySQL Workbench and run that migration
file manually.

## Tables

### `roles`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Role ID |
| `description` | `VARCHAR(500)` | Yes | | Human-readable description |
| `name` | `VARCHAR(255)` | No | Unique | Spring Security role name |

Required seed rows: `ROLE_USER` and `ROLE_ADMIN`.

### `users`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `enabled` | `BIT` | No | | Whether login is enabled |
| `failed_login_count` | `INTEGER` | Yes | | Failed login attempts |
| `locked` | `BIT` | No | | Whether the account is locked |
| `created_at` | `DATETIME(6)` | Yes | | Creation time |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | User ID |
| `role_id` | `BIGINT` | No | Foreign | References `roles.id` |
| `username` | `VARCHAR(50)` | No | Unique | Login username |
| `email` | `VARCHAR(100)` | No | Unique | User email |
| `full_name` | `VARCHAR(100)` | Yes | | User's full name |
| `password_hash` | `VARCHAR(255)` | No | | Encoded password |

Java initializes `enabled=false`, `failed_login_count=0`, and `locked=false`.
Registration changes `enabled` to true.

### `accounts`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `balance` | `FLOAT(53)` | No | | Current account balance |
| `version` | `INTEGER` | No | | Optimistic locking version |
| `created_at` | `DATETIME(6)` | Yes | | Creation time |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Account ID |
| `user_id` | `BIGINT` | No | Foreign | References `users.id` |
| `account_number` | `VARCHAR(255)` | No | Unique | Generated account number |
| `currency` | `VARCHAR(255)` | Yes | | Currency code |
| `status` | `ENUM('ACTIVE','FROZEN')` | Yes | | Account status |
| `type` | `ENUM('CHECKING','SAVINGS')` | Yes | | Account type |

Java initializes `balance=0`, `currency='USD'`, `status='ACTIVE'`, and
`version=0`. Account numbers have the form `ACCT-##########`.

### `transactions`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `amount` | `FLOAT(53)` | No | | Transaction amount |
| `created_at` | `DATETIME(6)` | No | | Transaction time |
| `from_account_id` | `BIGINT` | Yes | Foreign | References `accounts.id` |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Transaction ID |
| `to_account_id` | `BIGINT` | Yes | Foreign | References `accounts.id` |
| `tx_uuid` | `VARCHAR(36)` | No | Unique | Generated transaction UUID |
| `to_external_account` | `VARCHAR(255)` | Yes | | Optional external destination |
| `status` | `ENUM('COMPLETED','FAILED','PENDING')` | No | | Transaction state |
| `type` | `ENUM('BONUS','DEPOSIT','TRANSFER','WITHDRAW')` | No | | Transaction type |

Deposits have no source account, withdrawals have no destination account,
and internal transfers use both account foreign keys.

### `scheduled_payments`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `amount` | `DECIMAL(18,2)` | Yes | | Payment amount |
| `enabled` | `BIT` | No | | Whether scheduling is active |
| `interval_days` | `INTEGER` | No | | Days between payments |
| `account_from_id` | `BIGINT` | No | Foreign | References `accounts.id` |
| `created_at` | `DATETIME(6)` | Yes | | Creation time |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Scheduled payment ID |
| `last_run` | `DATETIME(6)` | Yes | | Last execution time |
| `next_run` | `DATETIME(6)` | Yes | | Next execution time |
| `account_to` | `VARCHAR(255)` | Yes | | Destination account number |
| `currency` | `VARCHAR(255)` | Yes | | Currency code |

Java initializes `enabled=true` and `currency='USD'`.

### `otps`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `used` | `BIT` | No | | Whether the code was consumed |
| `created_at` | `DATETIME(6)` | Yes | | Creation time |
| `expires_at` | `DATETIME(6)` | Yes | | Expiration time |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | OTP ID |
| `user_id` | `BIGINT` | No | Foreign | References `users.id` |
| `code` | `VARCHAR(255)` | Yes | | OTP code |
| `purpose` | `ENUM('LOGIN','PASSWORD_RESET')` | Yes | | OTP purpose |

The entity and repository expect this table, but the current `OtpService`
stores active OTPs in application memory instead of writing to it.

### `kyc_documents`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Document ID |
| `uploaded_at` | `DATETIME(6)` | Yes | | Upload time |
| `user_id` | `BIGINT` | No | Foreign | References `users.id` |
| `content_type` | `VARCHAR(255)` | Yes | | Uploaded MIME type |
| `filename` | `VARCHAR(255)` | Yes | | Stored filename |
| `path` | `VARCHAR(255)` | Yes | | Filesystem path |
| `status` | `ENUM('APPROVED','PENDING','REJECTED')` | Yes | | Review status |

Java initializes `status='PENDING'`. The actual uploaded files are stored
under the configured `uploads` directory, not in MySQL.

### `audit_logs`

| Column | MySQL type | Null | Key | Purpose |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | No | Primary | Audit event ID |
| `timestamp` | `DATETIME(6)` | No | | Event time |
| `user_id` | `BIGINT` | Yes | | User ID recorded with the event |
| `action` | `VARCHAR(255)` | No | | Event name |
| `details` | `TEXT` | Yes | | Event details |
| `ip_address` | `VARCHAR(255)` | Yes | | Request IP address |

`audit_logs.user_id` is deliberately not a foreign key in the entity model.

## Relationships

| Child column | Parent column | Relationship |
| --- | --- | --- |
| `users.role_id` | `roles.id` | Many users to one role |
| `accounts.user_id` | `users.id` | Many accounts to one user |
| `transactions.from_account_id` | `accounts.id` | Optional source account |
| `transactions.to_account_id` | `accounts.id` | Optional destination account |
| `scheduled_payments.account_from_id` | `accounts.id` | Payment source account |
| `otps.user_id` | `users.id` | Many OTPs to one user |
| `kyc_documents.user_id` | `users.id` | Many documents to one user |

## Recoverable Data

The repository contains enough information to restore the database structure
and the required role records. It does not contain the lost user, account,
transaction, KYC, OTP, scheduled-payment, or audit-log rows, so those records
cannot be reconstructed from this project alone.
