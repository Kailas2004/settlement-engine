# Payment Settlement Engine

Payment Settlement Engine is a Spring Boot application that simulates how payment settlement systems work in production:

- transactions are captured,
- settlement runs on a schedule or manual trigger,
- locking prevents overlapping runs,
- retries are tracked,
- reconciliation classifies outcomes,
- exceptions are queued for operator action.

It includes a browser-based admin dashboard plus REST APIs for all core flows.

## Who This Is For

- Students learning distributed systems and backend workflow design.
- Engineers who want a clean reference for lock-safe batch processing.
- Anyone who wants to understand settlement lifecycle + reconciliation in one project.

## What It Does

- Create customers and merchants.
- Create `CAPTURED` transactions.
- Process settlements with Quartz scheduler (every 30 seconds).
- Process settlements with manual trigger API/UI.
- Use Redis lock so only one settlement run executes at a time.
- Support idempotent manual triggers with `Idempotency-Key`.
- Record settlement attempts in logs.
- Reconcile settlement outcomes into `PENDING`, `MATCHED`, `EXCEPTION_QUEUED`, and `RESOLVED`.
- Allow exception queue actions: retry and resolve with note.

## System Behavior

### Settlement State Machine

```text
CAPTURED -> PROCESSING -> SETTLED
                 |
                 +-> CAPTURED (retry++)
                 +-> FAILED (if max retries reached)
```

### Reconciliation State Machine

```text
PENDING -> MATCHED
PENDING -> EXCEPTION_QUEUED -> RESOLVED
PENDING -> EXCEPTION_QUEUED -> RETRY -> PENDING
```

### Reliability Guarantees

- Atomic claim before processing (`CAPTURED -> PROCESSING`).
- Redis lock for single active settlement runner.
- Startup recovery for stuck `PROCESSING` transactions.
- Idempotent manual trigger replay within configured TTL.
- Manual trigger processing visibility hold so `PROCESSING` is observable in UI/E2E.

## Tech Stack

- Java 17
- Spring Boot 4
- Spring Data JPA
- Quartz Scheduler
- Redis
- PostgreSQL (default) or H2 (local isolated runs)
- Vanilla HTML/CSS/JS
- Maven
- Playwright (E2E script)

## Project Structure

```text
src/main/java/com/kailas/settlementengine
  controller/    REST endpoints
  entity/        domain models and enums
  repository/    JPA repositories
  scheduler/     Quartz config and jobs
  service/       settlement, lock, idempotency, reconciliation, monitoring

src/main/resources/static
  index.html     admin UI
  app.js         frontend behavior and API calls
  style.css      styles

scripts/
  playwright-validate.mjs  automated E2E validation flow
```

## Quick Start

### Option A: Docker Compose

This repo's `Dockerfile` expects a built jar at `target/settlement-engine-0.0.1-SNAPSHOT.jar`.

1. Build:

```bash
./mvnw clean package -DskipTests
```

2. Start stack:

```bash
docker compose up --build
```

3. Open:

```text
http://localhost:8080
```

4. Stop:

```bash
docker compose down
```

### Option B: Local Run (PostgreSQL + Redis)

1. Start PostgreSQL and Redis.
2. Run app:

```bash
./mvnw spring-boot:run
```

3. Open `http://localhost:8080`.

### Option C: Local Isolated Run (H2 + Redis)

```bash
SPRING_DATASOURCE_URL=jdbc:h2:file:./data/devdb \
SPRING_DATASOURCE_USERNAME=sa \
SPRING_DATASOURCE_PASSWORD= \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
SPRING_DATA_REDIS_HOST=127.0.0.1 \
SPRING_DATA_REDIS_PORT=6379 \
./mvnw spring-boot:run
```

## Configuration

Use environment variables to override `application.properties`.

### Core Runtime

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | Database JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |

### Settlement Outcome

| Variable | Default | Notes |
|---|---|---|
| `SETTLEMENT_OUTCOME_MODE` | `RANDOM` | `RANDOM`, `ALWAYS_SUCCESS`, `ALWAYS_FAIL` |
| `SETTLEMENT_OUTCOME_RANDOM_SEED` | empty | Optional deterministic seed for `RANDOM` |

### Trigger Idempotency

| Variable | Default | Notes |
|---|---|---|
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_TTL_SECONDS` | `600` | Replay window for same idempotency key |
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_WAIT_TIMEOUT_MILLIS` | `5000` | Wait time for in-flight duplicate request |

### Processing Visibility (Manual Trigger)

| Variable | Default | Notes |
|---|---|---|
| `SETTLEMENT_PROCESSING_VISIBILITY_HOLD_MILLIS_MANUAL` | `2500` | Keeps `PROCESSING` visible in UI before final state |

## API Reference

### Customers

- `POST /customers`
- `GET /customers`

Example:

```bash
curl -X POST http://localhost:8080/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com"}'
```

### Merchants

- `POST /merchants`
- `GET /merchants`

### Transactions

- `POST /transactions?customerId={id}&merchantId={id}&amount={value}`
- `GET /transactions`

### Settlement

- `POST /settlement/trigger`
- Optional header: `Idempotency-Key: <key>`

Example:

```bash
curl -X POST http://localhost:8080/settlement/trigger \
  -H "Idempotency-Key: trigger-001"
```

### Logs

- `GET /logs`

### Monitoring

- `GET /api/settlements/stats`

### Reconciliation

- `POST /api/reconciliation/run`
- `GET /api/reconciliation/exceptions`
- `POST /api/reconciliation/exceptions/{transactionId}/retry`
- `POST /api/reconciliation/exceptions/{transactionId}/resolve`

Resolve payload example:

```json
{ "note": "Manually verified and closed" }
```

## Frontend Screens

### Dashboard

Live totals, lock state, queue state, and last run metadata.

![Dashboard](docs/screenshots/step1_homepage.png)

### Customer and Merchant Management

Create and review records from UI tables.

![Customer](docs/screenshots/step2_customer_created.png)
![Merchant](docs/screenshots/step3_merchant_created.png)

### Transaction Flow

Create transactions and observe settlement progression.

![Transaction Captured](docs/screenshots/step4_transaction_captured.png)
![Transaction Settled](docs/screenshots/step5_transaction_settled.png)

### Lock and Audit

Observe lock acquisition/release and verify settlement logs.

![Lock Status](docs/screenshots/step6_lock_status.png)
![Settlement Logs](docs/screenshots/step7_logs.png)

### Idempotency

Duplicate triggers do not duplicate settled effects.

![Idempotency](docs/screenshots/step8_idempotency.png)

## Testing

### Backend Tests

```bash
./mvnw test
```

### Playwright End-to-End Validation

Install once:

```bash
npm install
```

Run:

```bash
BASE_URL=http://localhost:8080 SCREENSHOT_DIR=playwright-screenshots node scripts/playwright-validate.mjs
```

Validation covers:

- application load
- customer creation
- merchant creation
- transaction creation
- settlement transition and lock behavior
- logs verification
- idempotency behavior

## Troubleshooting

### "Failed to run reconciliation" in UI

The UI now shows detailed backend error responses. Check:

- app port/URL is correct
- Redis is reachable
- backend is running latest code

### `PROCESSING` not visible during fast manual runs

Increase:

- `SETTLEMENT_PROCESSING_VISIBILITY_HOLD_MILLIS_MANUAL`

### Port already in use

Run on a different port:

```bash
SERVER_PORT=18080 ./mvnw spring-boot:run
```

## Current Status

The project currently has working end-to-end coverage for:

- settlement processing
- distributed locking
- idempotent manual triggering
- reconciliation + exception queue
- operational dashboard monitoring
