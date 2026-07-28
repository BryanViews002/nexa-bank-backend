-- Align the ledger tables created in V2 with the current JPA entities.

-- V2 did not record posting direction, so existing rows cannot be migrated
-- without inventing accounting data. Fail before permanent DDL if any exist.
CREATE TEMPORARY TABLE ledger_postings_v6_guard (
    is_empty INT NOT NULL
);

INSERT INTO ledger_postings_v6_guard (is_empty)
SELECT CASE WHEN COUNT(*) = 0 THEN 1 ELSE NULL END
FROM ledger_postings;

DROP TABLE ledger_postings_v6_guard;

ALTER TABLE ledger_journals
    ADD COLUMN transaction_id BIGINT;

ALTER TABLE ledger_journals
    ADD COLUMN event_type VARCHAR(60);

ALTER TABLE ledger_journals
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'POSTED';

UPDATE ledger_journals
SET event_type = 'LEGACY'
WHERE event_type IS NULL OR event_type = '';

UPDATE ledger_journals
SET description = 'Legacy ledger journal'
WHERE description IS NULL;

ALTER TABLE ledger_journals
    MODIFY COLUMN event_type VARCHAR(60) NOT NULL;

ALTER TABLE ledger_journals
    MODIFY COLUMN description VARCHAR(500) NOT NULL;

ALTER TABLE ledger_journals
    ADD CONSTRAINT uk_ledger_journals_transaction UNIQUE (transaction_id);

ALTER TABLE ledger_journals
    ADD CONSTRAINT fk_ledger_journals_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id);

ALTER TABLE ledger_postings
    ADD COLUMN direction VARCHAR(10) NOT NULL;

ALTER TABLE ledger_postings
    ADD CONSTRAINT chk_ledger_postings_direction
        CHECK (direction IN ('DEBIT', 'CREDIT'));
