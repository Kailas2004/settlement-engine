# Settlement Engine

A production-style payment settlement simulation focused on correctness, operational safety, and observability.

The project demonstrates the full lifecycle of customer/merchant transactions with:
- scheduled + manual settlement execution
- Redis locking for single-run safety
- idempotent trigger behavior
- reconciliation and exception handling
- role-based access (admin and user)

## Live Demo

- **URL:** https://settlement-engine-production.up.railway.app
- **Demo User (read-only):**
  - Username: `user`
  - Password: `user123`

Admin credentials are intentionally private.

## UI Preview

### Authentication

![Login](docs/screenshots/ui-login.png)

### Dashboard

| Admin Dashboard | User Dashboard |
|---|---|
| ![Admin Dashboard](docs/screenshots/ui-dashboard-admin.png) | ![User Dashboard](docs/screenshots/ui-dashboard-user.png) |

### Core Pages

| Customers | Merchants |
|---|---|
| ![Customers](docs/screenshots/ui-customers.png) | ![Merchants](docs/screenshots/ui-merchants.png) |

| Transactions | Reconciliation |
|---|---|
| ![Transactions](docs/screenshots/ui-transactions.png) | ![Reconciliation](docs/screenshots/ui-reconciliation.png) |

| Settlement Logs |
|---|
| ![Settlement Logs](docs/screenshots/ui-logs.png) |

## What This System Guarantees

- **Single settlement runner at a time** using Redis distributed lock coordination.
- **Safe retries for manual trigger** using idempotency-key semantics.
- **Deterministic transaction progression** with bounded retry and terminal failure behavior.
- **Clear exception lifecycle** through reconciliation queue, retry, and resolve flows.
- **Backend-enforced role policy** even if UI is tampered with.
- **Operational visibility** via lock/run telemetry and status dashboards.

## Tech Stack

- Java 17
- Spring Boot 4
- Spring Data JPA
- Spring Security
- Quartz Scheduler
- PostgreSQL
- Redis
- HTML / CSS / Vanilla JavaScript
- Maven
- Playwright (validation scripts)

## Architecture

```text
Browser UI (static pages)
    |
Spring Boot REST + Security
    |
+------------------------+------------------------+
| Settlement Core        | Reconciliation Service |
| (lock + idempotency)   | (exceptions + actions) |
+------------------------+------------------------+
    |
PostgreSQL (state/logs) + Redis (lock/idempotency)
```

## Roles and Access

- **ADMIN**
  - Can create customers/merchants/transactions
  - Can trigger settlement
  - Can run reconciliation and perform retry/resolve actions
- **USER**
  - Read-only access to dashboard and data tables

### Auth Endpoints

- `GET /login.html`
- `POST /login`
- `POST /logout`
- `GET /api/auth/me`

## Frontend Notes

- Custom sign-in page (`login.html`).
- Responsive admin dashboard layout.
- Table pagination is enabled across list pages with **max 10 rows per page**.
- UI branding and role label adapt to current logged-in role.

## API Summary

### Customers
- `GET /customers`
- `POST /customers` (ADMIN)

### Merchants
- `GET /merchants`
- `POST /merchants` (ADMIN)

### Transactions
- `GET /transactions`
- `POST /transactions?customerId={id}&merchantId={id}&amount={value}` (ADMIN)

### Settlement
- `POST /settlement/trigger` (ADMIN)
- Supports `Idempotency-Key` header

### Monitoring
- `GET /api/settlements/stats`

### Logs
- `GET /logs`

### Reconciliation
- `GET /api/reconciliation/exceptions`
- `POST /api/reconciliation/run` (ADMIN)
- `POST /api/reconciliation/exceptions/{transactionId}/retry` (ADMIN)
- `POST /api/reconciliation/exceptions/{transactionId}/resolve` (ADMIN)

Resolve request body:

```json
{ "note": "Manually verified and closed" }
```

## Local Development

### 1. Clone

```bash
git clone https://github.com/Kailas2004/settlement-engine.git
cd settlement-engine
```

### 2. Start Dependencies

```bash
docker compose up -d postgres redis
```

### 3. Configure Credentials

```bash
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='<your-admin-password>'
export APP_USER_USERNAME=user
export APP_USER_PASSWORD='<your-user-password>'
```

### 4. Run App

```bash
./mvnw spring-boot:run
```

### 5. Open

```text
http://localhost:8080
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
| `SPRING_DATA_REDIS_USERNAME` | Redis username (optional) |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password (optional) |
| `PORT` | Server port (Railway sets automatically) |

### Security

| Variable | Purpose |
|---|---|
| `APP_ADMIN_USERNAME` | Admin username |
| `APP_ADMIN_PASSWORD` | Admin password |
| `APP_USER_USERNAME` | User username |
| `APP_USER_PASSWORD` | User password |

### Settlement / Idempotency

| Variable | Default | Notes |
|---|---|---|
| `SETTLEMENT_OUTCOME_MODE` | `RANDOM` | `RANDOM`, `ALWAYS_SUCCESS`, `ALWAYS_FAIL` |
| `SETTLEMENT_OUTCOME_RANDOM_SEED` | empty | Optional deterministic seed |
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_TTL_SECONDS` | `600` | Replay window |
| `SETTLEMENT_TRIGGER_IDEMPOTENCY_WAIT_TIMEOUT_MILLIS` | `5000` | Wait for in-flight duplicate |

## Testing

### Backend

```bash
./mvnw test
```

### E2E Validation

```bash
npm install
BASE_URL=http://localhost:8080 \
SCREENSHOT_DIR=playwright-screenshots \
node scripts/playwright-validate.mjs
```

## Deployment (Railway)

This project is designed to auto-deploy on Railway from the connected GitHub branch.

- Push to your deploy branch.
- Railway rebuilds and updates the **same service URL**.
