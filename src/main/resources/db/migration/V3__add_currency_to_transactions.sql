-- Add currency column to transactions for auditability.
-- Existing rows (seed data) default to INR — the only supported currency.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'INR';
