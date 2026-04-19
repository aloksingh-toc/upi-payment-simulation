# UPI Payment Gateway Simulation

> **Note:** UPI (Unified Payments Interface) is a real-time payment system regulated by the [Reserve Bank of India (RBI)](https://www.rbi.org.in) and operated by [NPCI](https://www.npci.org.in). This simulation is scoped to the Indian financial ecosystem — it uses INR as the only supported currency and follows UPI's payment flow conventions. It is intended for use within India.

A production-grade REST API simulating a UPI payment gateway. Built with Java 21, Spring Boot 3, and PostgreSQL.

## Features

- **Payment initiation** with idempotency — duplicate requests return the same response
- **Webhook processing** with HMAC-SHA256 signature verification and async handling
- **Distributed locking** via PostgreSQL advisory locks to prevent double-credits
- **ACID ledger** — debit and transaction record saved atomically; credit applied on webhook confirmation
- **API key authentication** with constant-time comparison (timing-attack safe)
- **Flyway** database migrations
- **Docker** multi-stage build with non-root user and health check

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | Spring Security (Bearer API key + HMAC webhook) |
| Locking | PostgreSQL advisory locks (`pg_advisory_xact_lock`) |
| Testing | JUnit 5 · Mockito · Testcontainers |
| CI/CD | GitHub Actions |

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/payments` | Bearer API key | Initiate a payment |
| `GET` | `/api/v1/accounts/{id}/balance` | Bearer API key | Get account balance |
| `POST` | `/api/v1/webhooks/upi` | HMAC-SHA256 | Receive bank webhook |
| `GET` | `/actuator/health` | None | Health check |

### Initiate Payment

```http
POST /api/v1/payments
Authorization: Bearer <API_KEY>
Idempotency-Key: <uuid>
Content-Type: application/json

{
  "sender_id": "a0000000-0000-0000-0000-000000000001",
  "receiver_id": "a0000000-0000-0000-0000-000000000002",
  "amount": "500.00",
  "currency": "INR"
}
```

### Webhook (bank callback)

```http
POST /api/v1/webhooks/upi
X-Webhook-Signature: <hmac-sha256-hex>
Content-Type: application/json

{
  "transaction_id": "<uuid>",
  "status": "SUCCESS",
  "bank_reference_number": "BANKREF123"
}
```

## API Documentation

Swagger UI is available at **`http://localhost:8080/swagger-ui.html`** when running locally.

- Click **Authorize** → enter your `APP_API_KEY` to authenticate payment and account endpoints
- Webhook endpoints use HMAC-SHA256 — test them with a pre-computed signature
- Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`

> Swagger UI is disabled in the Docker/production profile for security.

## Running Locally

### Prerequisites

- Docker and Docker Compose
- Copy `.env.example` to `.env` and fill in the values

```bash
cp .env.example .env
```

### Start with Docker Compose

```bash
docker compose up --build
```

The API will be available at `http://localhost:8080`.

### Required Environment Variables

| Variable | Description |
|---|---|
| `APP_API_KEY` | Bearer token for authenticating API requests |
| `WEBHOOK_SECRET` | HMAC-SHA256 secret for verifying webhook signatures |
| `POSTGRES_PASSWORD` | PostgreSQL password |

## Running Tests

Tests use Testcontainers and spin up a real PostgreSQL instance — no manual setup required.

```bash
mvn test
```

## CI/CD

GitHub Actions runs on every push to `main` or `develop`:

1. **Compile** — fails fast on build errors
2. **Test** — unit + integration tests via Testcontainers
3. **Checkstyle** — enforces code style; fails build on violations
4. **OWASP scan** — fails build on CVEs with CVSS ≥ 7
5. **Package & Docker image** — only on `main` branch pushes

### GitHub Secrets Required

| Secret | Used for |
|---|---|
| `NVD_API_KEY` | OWASP NVD API (optional — scans still run without it, just slower) |

## Architecture

```
PaymentController
    └── PaymentService          (idempotency check → validate → lock → debit → save)
            ├── PaymentValidator    (self-transfer, sender/receiver existence)
            ├── LockService         (facade over DistributedLock interface)
            ├── LedgerService       (debit / credit via MoneyUtils)
            └── IdempotencyService  (store & retrieve with TOCTOU-safe double-check)

WebhookController
    └── HmacService             (constant-time HMAC-SHA256 verification)
    └── WebhookService          (@Async — acquires tx lock, applies credit)
```

## Security Notes

- API key comparison uses `MessageDigest.isEqual` (constant-time, prevents timing attacks)
- HMAC signature verification uses the same constant-time approach
- Passwords and secrets are never committed — use environment variables only
- Docker container runs as a non-root user (`appuser`)
- Only `/actuator/health` is exposed; all other actuator endpoints are disabled
