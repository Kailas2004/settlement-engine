# Payment Settlement Engine

`settlement-engine` is a Spring Boot backend service that simulates and manages transaction settlement workflows between customers and merchants.

It models real-world payment settlement behavior including retry logic, distributed locking, job scheduling, failure recovery, and operational monitoring.

---

## Features

* Transaction lifecycle management
  `CAPTURED → PROCESSING → SETTLED / FAILED`
* Manual settlement trigger via REST API
* Scheduled settlement execution using Quartz
* Distributed locking using Redis
* Retry-aware settlement processing with per-transaction retry counters
* Atomic transaction claiming to prevent duplicate processing
* Recovery of stuck `PROCESSING` transactions on application restart
* Operational monitoring endpoint for settlement metrics
* Audit trail via settlement attempt logs
* Dockerized multi-container setup (App + PostgreSQL + Redis)

---

## Tech Stack

* Java 17
* Spring Boot 4 (Web MVC, Data JPA, Quartz, Redis)
* PostgreSQL
* Redis
* Docker & Docker Compose
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
Dockerfile       Application container definition
docker-compose.yml Multi-container orchestration
```

---

# Running the Application

You can run the application either locally or using Docker.

---

## Option 1 — Run Locally (Without Docker)

### 1. Configure Database and Redis

Update:

```
src/main/resources/application.properties
```

Provide:

* PostgreSQL credentials
* Redis host and port

### 2. Start Application

```
./mvnw spring-boot:run
```

Application runs at:

```
http://localhost:8080
```

---

## Option 2 — Run with Docker (Recommended)

This project includes a full Docker Compose setup with:

* Spring Boot application container
* PostgreSQL container
* Redis container

### 1. Build and Start Containers

```
docker compose up --build
```

### 2. Stop Containers

```
docker compose down
```

### 3. Reset All Containers and Volumes

```
docker compose down -v
```

Application will be available at:

```
http://localhost:8080
```

No manual database or Redis setup required.

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

## Reliability & Infrastructure Design

* Settlement claiming is performed atomically to prevent duplicate execution.
* Redis-based distributed locking ensures only one scheduler instance processes settlements at a time.
* On startup, transactions stuck in `PROCESSING` are reverted to `CAPTURED`.
* Environment-variable–based configuration allows seamless local and containerized execution.
* Multi-container Docker setup simulates production-style infrastructure.

---

## Architectural Highlights

* Idempotent settlement execution
* Retry handling with bounded retry count
* Quartz-based scheduled job processing
* Redis-backed distributed lock mechanism
* Clean separation of controller, service, repository, and scheduler layers
* Dockerized infrastructure for reproducible environments



If you want, next we can optimize your Docker image with a multi-stage build to make it production-grade.
