-- Virtual Payment Address: UPI-style identifier for an account (e.g. alice@upi)
ALTER TABLE accounts ADD COLUMN vpa VARCHAR(255);

CREATE UNIQUE INDEX idx_accounts_vpa ON accounts (vpa);

-- Seed VPAs for the existing smoke-test accounts
UPDATE accounts SET vpa = 'alice@upi' WHERE account_id = 'a0000000-0000-0000-0000-000000000001';
UPDATE accounts SET vpa = 'bob@upi'   WHERE account_id = 'a0000000-0000-0000-0000-000000000002';
