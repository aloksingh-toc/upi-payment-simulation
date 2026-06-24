# VPA + Zero-Click E-Receipt — Feature Plan for UPI Payment Simulation

> **Revision note:** This supersedes the original e-receipt-only plan, which assumed
> a merchant QR-code/POS shape with a `QrGeneratorService` that does not exist in
> this codebase. This version is verified against the actual code in
> `com.upi.payment` and adds two related features in order: VPA (so this project
> is actually UPI-shaped, not just UUID-to-UUID transfers) and then the e-receipt,
> which builds on the same transaction/webhook flow either way.

## 0. Why VPA First

Real UPI payments are addressed by VPA (`name@bank`), not raw account IDs — a QR
code is just an encoding of a VPA payment request, so QR without VPA underneath is
decoration with nothing behind it. Today `accounts` has no `vpa` column and
`PaymentRequest` takes `senderId`/`receiverId` as raw UUIDs — verified in
`entity/Account.java` and `dto/request/PaymentRequest.java`. Adding VPA is the
minimum to make this an actual UPI simulation rather than a generic A2A transfer API.

QR generation is still out of scope for this branch — it's a thin rendering layer on
top of VPA strings (`upi://pay?pa=<vpa>&am=<amount>`) and can be added later without
touching the data model again.

---

## 1. Feature A — VPA Support

### 1.1 Database Change
New migration `V4__add_vpa_to_accounts.sql`:
```sql
ALTER TABLE accounts ADD COLUMN vpa VARCHAR(255);
CREATE UNIQUE INDEX idx_accounts_vpa ON accounts (vpa);
```
Nullable initially so existing seeded accounts don't break; uniqueness enforced once
set. (A follow-up migration can backfill + make it `NOT NULL` once all accounts have
one, but that's not needed for this feature to work.)

### 1.2 Entity Change
`Account.java` gets a `vpa` field (`@Column(name = "vpa")`) with the existing
getter/setter pattern already used for `currency`.

### 1.3 Request Change
`PaymentRequest` adds an optional `receiverVpa` field alongside the existing
`receiverId`. Exactly one of the two must be supplied — mirrors how real UPI apps
let you pay either a VPA or, for saved contacts, a resolved account reference.

### 1.4 Resolution Logic
New `VpaResolutionService` (in `service/`):
- `resolve(String vpa) -> UUID accountId` — looks up `Account` by `vpa`, throws
  `ResourceNotFoundException` if not found (same exception type already used in
  `PaymentValidator.requireReceiverExists`)

`PaymentValidator.validate()` calls this first when `receiverVpa` is present, then
proceeds with the existing self-transfer/exists checks using the resolved UUID — no
change to the locking or debit/credit logic in `PaymentService`, since by the time
it reaches `PaymentService` it's still just an account UUID underneath.

### 1.5 New/Modified Files
```
src/main/java/com/upi/payment/
├── entity/Account.java                 ← MODIFIED: add vpa field
├── dto/request/PaymentRequest.java     ← MODIFIED: add receiverVpa field
├── service/
│   ├── VpaResolutionService.java       ← NEW
│   └── PaymentValidator.java           ← MODIFIED: resolve VPA before existing checks
src/main/resources/db/migration/
└── V4__add_vpa_to_accounts.sql         ← NEW
```

### 1.6 Tests
- `VpaResolutionServiceTest` — found/not-found cases
- `PaymentValidatorTest` — new tests for VPA-based requests (valid VPA resolves,
  unknown VPA throws, both `receiverId` and `receiverVpa` supplied throws, neither
  supplied throws)
- Integration test: `POST /api/v1/payments` with `receiverVpa` instead of
  `receiverId` succeeds end-to-end

**Estimate: ~1.5 days**

---

## 2. Feature B — Zero-Click E-Receipt

(Unchanged in substance from the prior revision — included here so this is one
single plan document for the branch.)

### 2.1 What It Is
A short-link receipt generated at payment-initiation time, showing transaction
status (PENDING → CONFIRMED/FAILED/REFUNDED), amount, currency, timestamps. Updates
automatically when the existing async webhook flow (`WebhookService`) settles the
transaction — no polling, no manual receipt step.

### 2.2 New Migration
`V5__create_receipt_short_links.sql` (V5, since V4 is now taken by the VPA column):
```sql
CREATE TABLE receipt_short_links (
    token           VARCHAR(12) PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES transactions(transaction_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMP
);

CREATE INDEX idx_receipt_short_links_transaction_id
    ON receipt_short_links (transaction_id);
```

### 2.3 New/Modified Files
```
src/main/java/com/upi/payment/
├── entity/ReceiptShortLink.java              ← NEW
├── repository/ReceiptShortLinkRepository.java ← NEW
├── service/
│   ├── ShortLinkService.java                 ← NEW
│   ├── PaymentService.java                   ← MODIFIED: create short-link on initiation
│   └── WebhookService.java                   ← MODIFIED: mark CONFIRMED/FAILED on webhook
├── controller/ReceiptController.java         ← NEW: GET /receipt/{token}
├── dto/response/
│   ├── ReceiptResponse.java                  ← NEW
│   └── PaymentResponse.java                  ← MODIFIED: add receiptUrl field
└── config/SecurityConfig.java                ← MODIFIED: permitAll() for /receipt/**
```

### 2.4 Token Generation
8-char token via `SecureRandom` + Base62 — no new dependency, no `jnanoid`.

### 2.5 Wiring
- `PaymentService.initiatePayment()`: after `transactionRepository.save(...)`, call
  `shortLinkService.create(tx)`, include token/URL in `PaymentResponse`
- `WebhookService.creditReceiver()` / `refundSender()`: call
  `shortLinkService.markConfirmed(txId)` / `markFailed(txId)` — both already run
  inside the existing transaction lock, no change to lock ordering
- `SecurityConfig`: add `/receipt/**` to the existing `permitAll()` list, same
  pattern as `/api/v1/webhooks/**`

### 2.6 Tests
- `ShortLinkServiceTest` — create/find/markConfirmed/markFailed
- Integration test: initiate payment → assert `receiptUrl` present → fire webhook →
  `GET /receipt/{token}` → assert status flips to CONFIRMED
- `ReceiptControllerTest` — 404 on unknown token

**Estimate: ~2 days**

---

## 3. Scope Exclusions (Both Features)

- QR code generation/rendering — explicitly deferred; VPA strings are the
  prerequisite, QR is a thin encoding layer that can be added later without
  touching the data model
- Redis / any caching layer — unjustified at this scale; indexed Postgres lookups
  are already sub-5ms for both VPA resolution and receipt token lookup
- SMS/WhatsApp delivery, admin dashboard, analytics, receipt expiry, HTML views

---

## 4. Implementation Order (this branch)

1. VPA migration + entity field
2. `VpaResolutionService` + `PaymentValidator` wiring + tests
3. Receipt migration + entity/repo
4. `ShortLinkService` + tests
5. Wire into `PaymentService` / `WebhookService`
6. `ReceiptController` + `SecurityConfig` + tests
7. Update README with both new capabilities

**Total: ~3.5 days**

---

## 5. Known Limitations (Document Honestly)

| Limitation | Detail |
|---|---|
| VPA uniqueness is app-level, not bank-verified | This is a simulation — there's no real bank registry behind the `vpa` column, just a unique constraint |
| Not a real UPI receipt | No connection to live NPCI rails — this simulates the backend confirmation flow only |
| Receipt token is unauthenticated | Anyone with the token can view the receipt (acceptable — it's a public link by design, like a shipping tracking link) |
| No receipt expiry by default | `receipt_short_links` rows are permanent unless a follow-up adds `expires_at` |
| QR still not implemented | VPA is the prerequisite; QR rendering is a separate, smaller follow-up once VPA lands |
