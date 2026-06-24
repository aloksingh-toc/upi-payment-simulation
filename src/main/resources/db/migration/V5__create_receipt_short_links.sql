-- Public short-link receipts, one per transaction, updated on webhook settlement
CREATE TABLE receipt_short_links (
    token           VARCHAR(12)   PRIMARY KEY,
    transaction_id  UUID          NOT NULL REFERENCES transactions(transaction_id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at    TIMESTAMP
);

CREATE INDEX idx_receipt_short_links_transaction_id
    ON receipt_short_links (transaction_id);
