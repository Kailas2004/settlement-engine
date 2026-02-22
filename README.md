
# Payment Settlement Engine

Production-style settlement simulation built with Spring Boot, PostgreSQL, Redis, and Quartz.
This project models real operational concerns: lock safety, idempotent triggers, retries, reconciliation, and role-based access.

## Live Demo

- URL: `https://settlement-engine-production.up.railway.app`
- Public demo (read-only):
  - Username: `user`
  - Password: `user123`
- Admin credentials are intentionally private.

## What This Project Demonstrates

- End-to-end settlement lifecycle (`CAPTURED -> PROCESSING -> SETTLED/FAILED`)
- Quartz scheduler (every 30 seconds) + manual trigger path
- Redis lock to prevent concurrent settlement runners
- Idempotent manual trigger behavior with `Idempotency-Key`
- Retry accounting and settlement logging
- Reconciliation and exception queue operations (`retry` / `resolve`)
- Role-based access control across frontend and backend (`ADMIN`, `USER`)

## Architecture

```text
                +---------------------------+
                |   Browser Dashboard UI    |
                |  (HTML/CSS/Vanilla JS)    |
                +-------------+-------------+
                              |
                              v
                  +-----------+-----------+
                  |   Spring Boot App     |
                  |   REST + Security     |
                  +-----------+-----------+
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
 +--------+---------+ +-------+--------+ +--------+---------+
 | Settlement Core  | | Reconciliation | | Monitoring/API   |
 | + Idempotency    | | + Exception Q  | | lock/run stats   |
 +--------+---------+ +-------+--------+ +--------+---------+
          |                   |                   |
          +-------------------+-------------------+
                              |
               +--------------+---------------+
               |                              |
               v                              v
      +--------+---------+           +--------+---------+
      | PostgreSQL       |           | Redis            |
      | customers/tx/log |           | distributed lock |
      +------------------+           +------------------+

                       +--------------------------+
                       | Quartz Scheduler (30s)   |
                       | triggers settlement job  |
                       +--------------------------+
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

- Redis lock for single-run safety:
  - Prevents overlapping settlement execution when scheduler and manual trigger collide.
- Server-side idempotency for manual triggers:
  - Duplicate `Idempotency-Key` requests replay safe responses without duplicate processing.
- Atomic status claim (`CAPTURED -> PROCESSING`) before settlement:
  - Reduces race conditions and double-processing risk.
- Reconciliation separated from settlement:
  - Keeps operational exception handling explicit and auditable.
- Role-based enforcement at both UI and API layers:
  - UI hides admin actions; backend still enforces authorization (`403`) as source of truth.
- Operational visibility first:
  - Dashboard exposes lock lifecycle, run metadata, queue depth, and status counts.

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

`http://localhost:8080`

## Roles and Access Model

- `ADMIN`:
  - Full read/write access
  - Can create entities, trigger settlements, run reconciliation, retry/resolve exceptions
- `USER`:
  - Read-only dashboard/tables
  - Write APIs blocked with `403`

Auth endpoints:

- `GET /login`, `POST /login`
- `POST /logout`
- `GET /api/auth/me`

## API Summary

- Customers:
  - `GET /customers`
  - `POST /customers` (`ADMIN`)
- Merchants:
  - `GET /merchants`
  - `POST /merchants` (`ADMIN`)
- Transactions:
  - `GET /transactions`
  - `POST /transactions?customerId={id}&merchantId={id}&amount={value}` (`ADMIN`)
- Settlement:
  - `POST /settlement/trigger` (`ADMIN`)
  - Supports `Idempotency-Key` header
- Monitoring:
  - `GET /api/settlements/stats`
- Logs:
  - `GET /logs`
- Reconciliation:
  - `GET /api/reconciliation/exceptions`
  - `POST /api/reconciliation/run` (`ADMIN`)
  - `POST /api/reconciliation/exceptions/{transactionId}/retry` (`ADMIN`)
  - `POST /api/reconciliation/exceptions/{transactionId}/resolve` (`ADMIN`)

Resolve request body:

```json
{ "note": "Manually verified and closed" }
```

## Environment Variables

### Core Runtime

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `SPRING_DATA_REDIS_HOST` | Redis host |
| `SPRING_DATA_REDIS_PORT` | Redis port |
| `SPRING_DATA_REDIS_USERNAME` | Redis username (if required) |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password (if required) |
| `PORT` | Server port (provided automatically on Railway) |

### Security

| Variable | Purpose |
|---|---|
| `APP_ADMIN_USERNAME` | Admin login username |
| `APP_ADMIN_PASSWORD` | Admin login password |
| `APP_USER_USERNAME` | User login username |
| `APP_USER_PASSWORD` | User login password |

### Settlement and Idempotency

| Variable | Default | Notes |
|---|---|---|
| `SETTLEMENT_OUTCOME_MODE` | `RANDOM` | `RANDOM`, `ALWAYS_SUCCESS`, `ALWAYS_FAIL` |
| `SETTLEMENT_OUTCOME_RANDOM_SEED` | empty | Optional deterministic seed |
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_TTL_SECONDS` | `600` | Replay window for keys |
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_WAIT_TIMEOUT_MILLIS` | `5000` | Wait for in-flight duplicate key |

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

- Dashboard: `docs/screenshots/step1_homepage.png`
- Customer creation: `docs/screenshots/step2_customer_created.png`
- Merchant creation: `docs/screenshots/step3_merchant_created.png`
- Transaction captured: `docs/screenshots/step4_transaction_captured.png`
- Transaction settled: `docs/screenshots/step5_transaction_settled.png`
- Lock status: `docs/screenshots/step6_lock_status.png`
- Settlement logs: `docs/screenshots/step7_logs.png`
- Idempotency: `docs/screenshots/step8_idempotency.png`

## Deployment (Railway)

Required managed services:

- PostgreSQL
- Redis

Required app variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `SPRING_DATA_REDIS_USERNAME`
- `SPRING_DATA_REDIS_PASSWORD`
- `APP_ADMIN_USERNAME`
- `APP_ADMIN_PASSWORD`
- `APP_USER_USERNAME`
- `APP_USER_PASSWORD`

## Future Improvements

- Replace in-memory auth with JWT/OAuth2 + persistent user store
- Add API versioning and OpenAPI spec publication
- Add transaction outbox/event streaming for async settlement workflows
- Add metrics + tracing (Micrometer + Prometheus + OpenTelemetry)
- Add dead-letter handling and backoff policies for failed jobs
- Add multi-instance test profile to validate lock behavior under load
- Expand CI pipeline with automated Playwright + contract tests

## Troubleshooting

- Reconciliation errors:
  - Validate Redis and datasource connectivity.
- Logout appears stale after deploy:
  - Hard refresh (`Cmd + Shift + R`) to reload static assets.
- Railway startup failures:
  - Usually missing `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, or auth vars.

---
If you are reviewing this project as an employer/recruiter, use the live demo and the role-based E2E script to verify behavior quickly.
