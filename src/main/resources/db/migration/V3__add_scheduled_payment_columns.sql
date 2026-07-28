-- Align scheduled_payments with the ScheduledPayment entity
-- (V2 updated accounts and transactions but left this table at its V1 shape)

ALTER TABLE scheduled_payments
    ADD COLUMN description   VARCHAR(500),
    ADD COLUMN category      VARCHAR(80),
    ADD COLUMN last_error    VARCHAR(1000),
    ADD COLUMN failure_count INT NOT NULL DEFAULT 0,
    ADD COLUMN max_failures  INT NOT NULL DEFAULT 3,
    ADD COLUMN version       INT NOT NULL DEFAULT 0;

UPDATE scheduled_payments SET category = 'BILLS' WHERE category IS NULL;
