# Settlement Engine

`settlement-engine` is a Spring Boot backend service that simulates and manages transaction settlement workflows between customers and merchants.

It models real-world payment settlement behavior including retry logic, distributed locking, job scheduling, failure recovery, and operational monitoring.

---

## Features

* Transaction lifecycle management:
  `CAPTURED → PROCESSING → SETTLED / FAILED`
* Manual settlement trigger via REST API
* Scheduled settlement execution using Quartz
* Distributed locking using Redis
* Retry-aware settlement processing with per-transaction retry counters
* Atomic transaction claiming to prevent duplicate processing
* Recovery of stuck `PROCESSING` transactions on application restart
* Operational monitoring endpoint for settlement metrics
* Audit trail via settlement attempt logs

---

## Tech Stack

* Java 17
* Spring Boot (Web MVC, Data JPA, Quartz, Redis)
* PostgreSQL
* Maven

---

## Project Structure

```
controller/      REST APIs (customers, merchants, transactions, settlement, monitoring)
service/         Business logic (settlement execution, retry handling, locking)
repository/      JPA repositories
entity/          Domain models and transaction status enums
scheduler/       Quartz job configuration
resources/static Minimal frontend (index.html, app.js, style.css)
```

---

## Getting Started

### 1. Configure Database and Redis

Update connection properties in:

```
src/main/resources/application.properties
```

Set:

* PostgreSQL credentials
* Redis host and port

---

### 2. Run the Application

```
./mvnw spring-boot:run
```

Application runs at:

```
http://localhost:8080
```

---

## API Endpoints

### Customers

```
POST   /customers
GET    /customers
```

### Merchants

```
POST   /merchants
GET    /merchants
```

### Transactions

```
POST   /transactions?customerId={id}&merchantId={id}&amount={value}
GET    /transactions
```

### Settlement

```
POST   /settlement/trigger
```

### Logs

```
GET    /logs
```

### Monitoring

```
GET    /api/settlements/stats
```

---

## Reliability Notes

* Settlement claiming is performed atomically to reduce duplicate execution.
* Redis lock ensures only one instance executes the scheduled settlement job.
* On startup, transactions stuck in `PROCESSING` are reverted to `CAPTURED`.

---
