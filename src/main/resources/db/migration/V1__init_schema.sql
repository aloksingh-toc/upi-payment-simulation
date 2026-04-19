-- Ledger accounts
CREATE TABLE IF NOT EXISTS accounts (
    account_id  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    currency    VARCHAR(3)   NOT NULL    DEFAULT 'INR',
    created_at  TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0)
);

-- Payment transactions
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id            UUID          NOT NULL REFERENCES accounts(account_id),
    receiver_id          UUID          NOT NULL REFERENCES accounts(account_id),
    amount               NUMERIC(19,4) NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    bank_reference_number VARCHAR(100),
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT transactions_status_check    CHECK (status IN ('PENDING','SUCCESS','FAILED'))
);

-- Idempotency cache — unique key prevents duplicate processing
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    api_path         VARCHAR(500) NOT NULL,
    request_payload  TEXT         NOT NULL,
    response_payload TEXT,
    status_code      INTEGER,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for hot query paths
CREATE INDEX IF NOT EXISTS idx_transactions_sender_id   ON transactions(sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver_id ON transactions(receiver_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status      ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_accounts_user_id         ON accounts(user_id);

-- Seed accounts for local development / smoke-testing
INSERT INTO accounts (account_id, user_id, balance, currency) VALUES
    ('a0000000-0000-0000-0000-000000000001','b0000000-0000-0000-0000-000000000001', 50000.0000,'INR'),
    ('a0000000-0000-0000-0000-000000000002','b0000000-0000-0000-0000-000000000002', 20000.0000,'INR')
ON CONFLICT DO NOTHING;
