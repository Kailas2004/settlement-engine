# Payment Settlement Engine

A Spring Boot application that simulates a payment settlement lifecycle between customers and merchants, with scheduling, retries, distributed locking, monitoring, and an admin frontend.

## What This Project Does

- Creates customers and merchants
- Creates captured transactions
- Settles captured transactions through manual trigger or scheduler
- Uses Redis lock to prevent overlapping settlement execution
- Tracks retries and writes settlement attempt logs
- Exposes operational monitoring stats to a frontend dashboard
- Supports deterministic settlement modes for testing (`RANDOM`, `ALWAYS_SUCCESS`, `ALWAYS_FAIL`)
- Supports idempotent manual trigger calls with `Idempotency-Key`

## Tech Stack

- Java 17
- Spring Boot 4
- Spring Data JPA
- Quartz Scheduler
- Redis (distributed lock)
- PostgreSQL / H2
- Vanilla HTML/CSS/JS frontend
- Maven

## Project Structure

```text
src/main/java/com/kailas/settlementengine
  controller/    REST APIs
  service/       settlement, lock, monitoring, idempotency logic
  repository/    JPA repositories
  entity/        domain entities and enums
  scheduler/     Quartz job configuration

src/main/resources/static
  index.html     admin UI shell
  app.js         frontend logic
  style.css      styling
```

## Quick Start

### Option A: Docker (recommended)

```bash
docker compose up --build
```

Open: `http://localhost:8080`

Stop:

```bash
docker compose down
```

### Option B: Local run

1. Start Redis and PostgreSQL (or override to H2 for local testing).
2. Run:

```bash
./mvnw spring-boot:run
```

Open: `http://localhost:8080`

## Configuration

`src/main/resources/application.properties` supports env-based overrides.

### Core

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`

### Settlement Outcome Mode

- `SETTLEMENT_OUTCOME_MODE`
  - `RANDOM` (default)
  - `ALWAYS_SUCCESS`
  - `ALWAYS_FAIL`
- `SETTLEMENT_OUTCOME_RANDOM_SEED` (optional, used only with `RANDOM`)

### Trigger Idempotency

- `SETTLEMENT_TRIGGER_IDEMPOTENCY_TTL_SECONDS` (default `600`)
- `SETTLEMENT_TRIGGER_IDEMPOTENCY_WAIT_TIMEOUT_MILLIS` (default `5000`)

## API Endpoints

### Customers

- `POST /customers`
- `GET /customers`

### Merchants

- `POST /merchants`
- `GET /merchants`

### Transactions

- `POST /transactions?customerId={id}&merchantId={id}&amount={value}`
- `GET /transactions`

### Settlement

- `POST /settlement/trigger`
- Optional header: `Idempotency-Key: your-key`

### Logs

- `GET /logs`

### Monitoring

- `GET /api/settlements/stats`

## Settlement Lifecycle

```text
CAPTURED -> PROCESSING -> SETTLED
                 |
                 +-> CAPTURED (retry++)
                 +-> FAILED (when max retries reached)
```

## Reliability Features

- Atomic transaction claim to avoid duplicate processing
- Redis lock around settlement execution (manual and scheduled)
- Startup recovery for stuck `PROCESSING` records
- Trigger idempotency replay via `Idempotency-Key`
- Monitoring includes lock acquisition/release metadata

## Frontend Walkthrough (Screenshots)

### 1) Home Dashboard

Shows live system metrics, lock state, queue state (`captured`/`processing`), and recent settlement run details.

![Home Dashboard](docs/screenshots/step1_homepage.png)

### 2) Customer Creation

Create a customer and verify it appears in the customer table with ID, name, and email.

![Customer Created](docs/screenshots/step2_customer_created.png)

### 3) Merchant Creation

Create a merchant and verify name, bank account, and settlement cycle in merchant table.

![Merchant Created](docs/screenshots/step3_merchant_created.png)

### 4) Transaction Creation

Create a transaction linked to customer and merchant. New records begin as `CAPTURED`.

![Transaction Captured](docs/screenshots/step4_transaction_captured.png)

### 5) Settlement Triggered

After trigger, transaction reaches settled state (timing of visible `PROCESSING` can be brief depending on runtime speed).

![Transaction Settled](docs/screenshots/step5_transaction_settled.png)

### 6) Lock Indicator Behavior

During rapid triggers, lock card reflects active/recent lock activity and prevents overlap.

![Lock Status](docs/screenshots/step6_lock_status.png)

### 7) Settlement Logs

Audit trail of attempts with `transactionId`, `attemptNumber`, `result`, and message.

![Settlement Logs](docs/screenshots/step7_logs.png)

### 8) Idempotency Validation

Repeated trigger after settlement keeps transaction state stable and avoids duplicate processing.

![Idempotency](docs/screenshots/step8_idempotency.png)

## Test Notes

Current test suite includes:

- Settlement outcome mode determinism tests
- Trigger idempotency service tests (sequential + concurrent)
- Controller-level idempotency behavior tests

Run tests:

```bash
./mvnw test
```

## Current Status

The project is functionally complete for a robust settlement simulation:

- Backend API and scheduler are working
- Locking and monitoring are working
- Frontend admin flows are working
- Deterministic test modes and idempotent manual trigger are implemented

