-- Allow REFUNDED as a valid transaction status.
-- The original CHECK constraint only permitted PENDING / SUCCESS / FAILED.
ALTER TABLE transactions DROP CONSTRAINT transactions_status_check;

ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'));
