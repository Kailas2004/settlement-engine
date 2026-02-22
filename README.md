# Payment Settlement Engine

A production-grade settlement simulation focused on reliability, correctness, and operational safety across the full payment lifecycle.
This is a systems-engineering project that emphasizes lock safety, idempotency, reconciliation, observability, and role-governed operations.

## Live Demo

- URL: https://settlement-engine-production.up.railway.app
- Public demo (read-only):
  - Username: user
  - Password: user123
- Admin credentials are intentionally private.

## Operational Guarantees Implemented

- Single-run settlement execution is enforced through Redis-based distributed locking.
- Duplicate manual trigger requests are safely replayed via idempotency-key semantics.
- Settlement processing follows deterministic lifecycle transitions with retry-bound failure handling.
- Scheduler and manual trigger paths are coordinated to prevent overlapping execution windows.
- Reconciliation state and exception workflows preserve operator-auditable outcomes.
- Role-based access is enforced at both UI and API layers, with backend authorization as source of truth.
- Runtime visibility is exposed through lock/run telemetry, queue depth, and status-level monitoring.

## Architecture

```text
+-----------------------------+
| Browser Dashboard (UI)      |
| HTML / CSS / Vanilla JS     |
+--------------+--------------+
               |
               v
+--------------+--------------+
| Spring Boot Application     |
| REST APIs + Security Layer  |
+--------------+--------------+
               |
   +-----------+-----------+------------------+
   |                       |                  |
   v                       v                  v
+--+----------------+ +----+--------------+ +--+------------------+
| Settlement Core   | | Reconciliation    | | Monitoring Service  |
| Lock + Idempotency| | Exception Queue   | | Stats + Lock/Run    |
+--+----------------+ +----+--------------+ +--+------------------+
   |                       |                  |
   +-----------+-----------+------------------+
               |
       +-------+-------+
       |               |
       v               v
+------+-------+ +-----+------+
| PostgreSQL   | | Redis      |
| tx/log/state | | lock state |
+--------------+ +------------+

+-------------------------------+
| Quartz Scheduler (every 30s)  |
| triggers settlement execution |
+-------------------------------+
```

## State Machines

### Settlement

```text
CAPTURED -> PROCESSING -> SETTLED
                 |
                 +-> CAPTURED (retry++)
                 +-> FAILED (max retries reached)
```

### Reconciliation

```text
PENDING -> MATCHED
PENDING -> EXCEPTION_QUEUED -> RESOLVED
PENDING -> EXCEPTION_QUEUED -> RETRY -> PENDING
```

## Design Decisions

- Redis lock for execution safety:
  - Prevents concurrent settlement runners when scheduler and manual triggers overlap.
- Idempotency service for trigger path:
  - Eliminates duplicate side-effects when clients retry the same operation.
- Explicit processing claim (CAPTURED -> PROCESSING) before terminal transition:
  - Reduces race risk and preserves traceable lifecycle progression.
- Reconciliation decoupled from settlement execution:
  - Keeps exception handling operationally explicit and auditable.
- Dual-layer authorization model:
  - UI hides privileged actions; backend authorization guarantees policy enforcement.
- Operational telemetry first:
  - Lock lifecycle and run metadata are surfaced to support production-style diagnosis.

## Tech Stack

- Java 17
- Spring Boot 4
- Spring Data JPA
- PostgreSQL
- Redis
- Quartz Scheduler
- Spring Security
- Vanilla HTML/CSS/JS
- Maven
- Playwright

## Repository Structure

```text
src/main/java/com/kailas/settlementengine
  controller/      REST endpoints
  service/         settlement, lock, idempotency, reconciliation, monitoring
  scheduler/       Quartz config and job
  entity/          domain models/enums
  repository/      JPA repositories
  config/          Spring Security configuration

src/main/resources/static
  index.html       dashboard shell
  app.js           frontend behavior/API integration
  style.css        styling

scripts/
  playwright-validate.mjs  settlement integration E2E
  role-validation.mjs      role-based E2E (ADMIN/USER)
```

## Quick Start

### 1. Clone

```bash
git clone https://github.com/Kailas2004/settlement-engine.git
cd settlement-engine
```

### 2. Start Dependencies

```bash
docker compose up -d postgres redis
```

### 3. Configure Auth Credentials

```bash
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='<your-admin-password>'
export APP_USER_USERNAME=user
export APP_USER_PASSWORD='<your-user-password>'
```

### 4. Run

```bash
./mvnw spring-boot:run
```

### 5. Open

```bash
http://localhost:8080
```

## Roles and Access Model

- ADMIN:
  - Full read/write access.
  - Can create entities, trigger settlement, run reconciliation, and perform exception actions.
- USER:
  - Read-only dashboard/tables.
  - Write APIs are rejected with 403.

Auth endpoints:

- GET /login, POST /login
- POST /logout
- GET /api/auth/me

## API Summary

- Customers:
  - GET /customers
  - POST /customers (ADMIN)
- Merchants:
  - GET /merchants
  - POST /merchants (ADMIN)
- Transactions:
  - GET /transactions
  - POST /transactions?customerId={id}&merchantId={id}&amount={value} (ADMIN)
- Settlement:
  - POST /settlement/trigger (ADMIN)
  - Supports Idempotency-Key header
- Monitoring:
  - GET /api/settlements/stats
- Logs:
  - GET /logs
- Reconciliation:
  - GET /api/reconciliation/exceptions
  - POST /api/reconciliation/run (ADMIN)
  - POST /api/reconciliation/exceptions/{transactionId}/retry (ADMIN)
  - POST /api/reconciliation/exceptions/{transactionId}/resolve (ADMIN)

Resolve request body:

```json
{ "note": "Manually verified and closed" }
```

## Environment Variables

### Core Runtime

| Variable | Purpose |
|---|---|
| SPRING_DATASOURCE_URL | PostgreSQL JDBC URL |
| SPRING_DATASOURCE_USERNAME | DB username |
| SPRING_DATASOURCE_PASSWORD | DB password |
| SPRING_DATA_REDIS_HOST | Redis host |
| SPRING_DATA_REDIS_PORT | Redis port |
| SPRING_DATA_REDIS_USERNAME | Redis username (if required) |
| SPRING_DATA_REDIS_PASSWORD | Redis password (if required) |
| PORT | Server port (provided automatically on Railway) |

### Security

| Variable | Purpose |
|---|---|
| APP_ADMIN_USERNAME | Admin login username |
| APP_ADMIN_PASSWORD | Admin login password |
| APP_USER_USERNAME | User login username |
| APP_USER_PASSWORD | User login password |

### Settlement and Idempotency

| Variable | Default | Notes |
|---|---|---|
| SETTLEMENT_OUTCOME_MODE | RANDOM | RANDOM, ALWAYS_SUCCESS, ALWAYS_FAIL |
| SETTLEMENT_OUTCOME_RANDOM_SEED | empty | Optional deterministic seed |
| SETTLEMENT_TRIGGER_IDEMPOTENCY_TTL_SECONDS | 600 | Replay window for keys |
| SETTLEMENT_TRIGGER_IDEMPOTENCY_WAIT_TIMEOUT_MILLIS | 5000 | Wait for in-flight duplicate key |

## Testing and Validation

### Backend Tests

```bash
./mvnw test
```

### Core Settlement E2E

```bash
npm install
BASE_URL=http://localhost:8080 \
SCREENSHOT_DIR=playwright-screenshots \
node scripts/playwright-validate.mjs
```

### Role-Based E2E

```bash
BASE_URL=http://localhost:8080 \
ADMIN_USERNAME=admin \
ADMIN_PASSWORD='<admin-password>' \
USER_USERNAME=user \
USER_PASSWORD='<user-password>' \
node scripts/role-validation.mjs
```

## Frontend Screenshots

### Dashboard

![Dashboard](docs/screenshots/step1_homepage.png)

### Customer Creation

![Customer Creation](docs/screenshots/step2_customer_created.png)

### Merchant Creation

![Merchant Creation](docs/screenshots/step3_merchant_created.png)

### Transaction Captured

![Transaction Captured](docs/screenshots/step4_transaction_captured.png)

### Transaction Settled

![Transaction Settled](docs/screenshots/step5_transaction_settled.png)

### Lock Status

![Lock Status](docs/screenshots/step6_lock_status.png)

### Settlement Logs

![Settlement Logs](docs/screenshots/step7_logs.png)

### Idempotency

![Idempotency](docs/screenshots/step8_idempotency.png)

## Deployment (Railway)

Required managed services:

- PostgreSQL
- Redis

Required app variables:

- SPRING_DATASOURCE_URL
- SPRING_DATASOURCE_USERNAME
- SPRING_DATASOURCE_PASSWORD
- SPRING_DATA_REDIS_HOST
- SPRING_DATA_REDIS_PORT
- SPRING_DATA_REDIS_USERNAME
- SPRING_DATA_REDIS_PASSWORD
- APP_ADMIN_USERNAME
- APP_ADMIN_PASSWORD
- APP_USER_USERNAME
- APP_USER_PASSWORD

## Future Enhancements

- Adopt the Transactional Outbox pattern for reliable event publication and downstream settlement notifications.
- Add multi-instance load test harness to validate distributed lock correctness under contention and failover.
- Introduce full observability stack (Micrometer metrics, Prometheus, Grafana, OpenTelemetry traces).
- Evolve authentication from in-memory users to persistent identity with JWT/OAuth2 and fine-grained RBAC.
- Add resilient job execution policies (dead-letter channel, exponential backoff, replay controls).
- Publish OpenAPI contracts and enforce backward-compatible API versioning strategy.
- Extend CI with integration matrices (DB/Redis profiles) and deterministic E2E verification gates.

## Troubleshooting

- Reconciliation errors:
  - Validate Redis and datasource connectivity.
- Logout appears stale after deploy:
  - Hard refresh static assets (Cmd + Shift + R).
- Railway startup failures:
  - Usually caused by missing SPRING_DATASOURCE_*, SPRING_DATA_REDIS_*, or auth vars.
