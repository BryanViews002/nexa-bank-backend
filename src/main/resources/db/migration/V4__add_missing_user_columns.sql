ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(30),
    ADD COLUMN address      VARCHAR(500),
    ADD COLUMN kyc_status   ENUM('NOT_SUBMITTED','PENDING','APPROVED','REJECTED')
                            NOT NULL DEFAULT 'NOT_SUBMITTED';
