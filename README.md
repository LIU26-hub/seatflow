# SeatFlow

Event registration and ticket booking service.

SeatFlow is a learning project whose goal is production grade engineering rather than
feature count: versioned database migrations, integration tests, containerised delivery,
observability and measured performance are all part of the definition of done.

## Domain

An organiser publishes an activity with a seat capacity and a registration window.
Users register while seats last. The hard problems are seat consistency under
concurrency, idempotent registration, rate limiting and asynchronous notifications.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Persistence | Spring Data JPA (Hibernate) |
| Database | MySQL 8 |
| Schema management | Flyway |
| Cache / coordination | Redis 7 (from milestone M3) |
| Messaging | RabbitMQ or RocketMQ (from milestone M4) |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana (from M5) |
| Build | Maven |

## Prerequisites

- JDK 17
- Maven 3.9 or newer
- Docker Desktop, or a locally installed MySQL 8 instance

## Quick start

1. Start the infrastructure (MySQL is published on host port **3307** so it does not
   collide with a MySQL instance already listening on 3306):

   ```bash
   cp .env.example .env
   docker compose up -d
   ```

2. Export the datasource settings. Then run the application:

   ```bash
   # macOS / Linux
   export DB_URL="jdbc:mysql://localhost:3307/seatflow?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
   export DB_USERNAME=root
   export DB_PASSWORD=seatflow
   mvn spring-boot:run
   ```

   ```powershell
   # Windows PowerShell
   $env:DB_URL="jdbc:mysql://localhost:3307/seatflow?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="seatflow"
   mvn spring-boot:run
   ```

3. Verify:

   ```bash
   curl http://localhost:8080/api/health
   curl http://localhost:8080/actuator/health
   curl http://localhost:8080/api/activities
   ```

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/health` | Application liveness and build timestamp |
| GET | `/actuator/health` | Actuator health, including database connectivity |
| GET | `/api/activities` | List published activities ordered by registration start |
| GET | `/api/activities/{id}` | Single activity, returns 404 when absent |

## Project layout

```
src/main/java/com/seatflow
├── SeatFlowApplication.java     application entry point
├── common                       cross cutting web concerns
│   ├── ApiResponse.java         single response envelope
│   ├── BusinessException.java   expected domain failures
│   └── GlobalExceptionHandler.java
├── health
│   └── HealthController.java
└── activity                     one package per feature
    ├── Activity.java            JPA entity
    ├── ActivityStatus.java
    ├── ActivityRepository.java
    ├── ActivityService.java     application logic
    ├── ActivityController.java  HTTP layer
    └── dto/ActivityResponse.java
```

Code is organised by feature, not by technical layer. Everything that changes
together lives together.

## Database

Schema is owned by Flyway scripts in `src/main/resources/db/migration`.
Hibernate runs with `ddl-auto: validate`, so a mismatch between an entity and a
table fails the application at startup instead of silently altering the schema.

Rules:

- never edit an applied migration, always add a new `V{n}__description.sql`
- no credentials in the repository, use environment variables or `application-local.yml`

## Conventions

- constructor injection everywhere, no field injection
- entities are never returned from controllers, always map to a DTO
- DTOs and simple value types are Java records
- one feature package per bounded capability

## Roadmap

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Skeleton: compose, Flyway, layering, health endpoint, README | in progress |
| M2 | Registration and authentication, validation, OpenAPI docs | planned |
| M3 | Seat consistency: Redis atomic decrement, idempotency, rate limiting | planned |
| M4 | Async notifications, container image, CI pipeline, deployment | planned |
| M5 | Metrics, load test report, one measured optimisation | planned |
| M5.5 | Retrieval augmented Q&A assistant | planned |
| M6 | English documentation, architecture diagram, interview write-up | planned |

## Licence

Personal learning project.
