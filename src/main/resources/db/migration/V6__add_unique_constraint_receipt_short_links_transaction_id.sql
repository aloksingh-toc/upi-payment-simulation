-- Enforce the one-receipt-per-transaction invariant at the DB level instead of
-- relying solely on application code (ShortLinkService.create is only ever
-- called once per transaction today, but nothing previously stopped a second
-- row for the same transaction_id from being inserted).
ALTER TABLE receipt_short_links
    ADD CONSTRAINT uq_receipt_short_links_transaction_id UNIQUE (transaction_id);
