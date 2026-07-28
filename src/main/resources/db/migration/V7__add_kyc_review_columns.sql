ALTER TABLE kyc_documents
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE kyc_documents
    ADD COLUMN reviewed_at DATETIME(6);

ALTER TABLE kyc_documents
    ADD COLUMN reviewed_by_user_id BIGINT;
