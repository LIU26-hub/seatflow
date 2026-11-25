# SeatFlow

Event registration and ticket booking service.

SeatFlow is a learning project whose goal is production grade engineering rather than
feature count: versioned database migrations, integration tests, containerised delivery,
observability and measured performance are all part of the definition of done.

It is a full stack application: a Spring Boot backend, a Vue 3 single page frontend, and
a Docker Compose stack that wires them together with MySQL, Redis Stack, RabbitMQ,
Prometheus and Grafana.

## Domain

An organiser publishes an activity with a seat capacity and a registration window.
Users register while seats last, then confirm within a time limit or lose the seat.
The hard problems are seat consistency under concurrency, idempotent registration,
rate limiting and asynchronous notifications.

Three roles drive the UI and the API:

- **PARTICIPANT** — browse activities, register / confirm / cancel, read notifications.
- **ORGANIZER** — create and publish activities, view the registration roster.
- **ADMIN** — everything above, plus user search and role assignment.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| Persistence | Spring Data JPA (Hibernate), Flyway migrations |
| Database | MySQL 8 |
| Cache / coordination | Redis Stack (RediSearch vector index + seat gate + rate limit) |
| Messaging | RabbitMQ 4 (outbox-backed notifications) |
| Security | Stateless JWT (HMAC), role-based access |
| AI assistant | Spring AI, SiliconFlow (Qwen3-8B chat + bge-m3 embeddings), RAG |
| Observability | Actuator, Micrometer, Prometheus, Grafana |
| Frontend | Vue 3 + TypeScript + Vite 6, Element Plus, Pinia, Vue Router, TanStack Vue Query |
| Frontend delivery | nginx (multi-stage Docker image) |
| Build | Maven (backend), npm (frontend) |

## Prerequisites

The recommended path runs everything in containers, so you only need:

- Docker Desktop (with Compose v2)

For local development without containers you additionally need JDK 17, Maven 3.9+,
and Node.js 22+ for the frontend.

## Quick start (full stack)

1. Create your environment file and fill in the secrets:

   ```bash
   cp .env.example .env
   # edit .env: JWT_SECRET, SILICONFLOW_API_KEY, and the service passwords
   ```

   Compose treats the secrets as **required** — it fails fast with
   `required variable ... is missing a value` if `.env` is absent or incomplete.
   No secret is baked into an image; values are injected at container runtime.

2. Build and start the whole stack:

   ```bash
   docker compose up -d --build
   ```

3. Open the app at **http://localhost** (nginx serves the frontend and proxies
   `/api` and `/actuator` to the backend). Sign in with the seeded administrator:

   | Username | Password | Role |
   | --- | --- | --- |
   | `admin` | `Admin@12345` | ADMIN |

   The seed admin is for local development only — change it before any real use.

4. Verify the backend through the nginx entry point:

   ```bash
   curl http://localhost/api/health
   curl http://localhost/actuator/health
   curl http://localhost/api/activities
   ```

### Service ports

| Service | Host port | Notes |
| --- | --- | --- |
| web (nginx) | 80 | frontend + API proxy, `WEB_PORT` overrides |
| app | 8080 | Spring Boot |
| mysql | 3307 | host 3307 → container 3306, avoids a local MySQL clash |
| redis | 6379 | Redis Stack |
| rabbitmq | 5672 / 15672 | broker / management console |
| prometheus | 9090 | metrics |
| grafana | 3000 | dashboards (admin / `GRAFANA_PASSWORD`) |

## Local development

Run the infrastructure in containers and the two apps on the host for hot reload.

Backend (reads `DB_*` from the environment or `application-local.yml`):

```bash
docker compose up -d mysql redis rabbitmq
export DB_URL="jdbc:mysql://localhost:3307/seatflow?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
export DB_USERNAME=root
export DB_PASSWORD=seatflow
mvn spring-boot:run
```

Frontend (Vite dev server on :5173, proxies `/api` and `/actuator` to :8080):

```bash
cd ../seatflow-web
npm install
npm run dev
```

## Configuration and secrets

All secrets live in `.env` (gitignored) and are interpolated into compose at runtime.
The images contain only placeholders such as `${DB_PASSWORD:1234}` and
`${JWT_SECRET:dev-only-...}`; `docker inspect` on an image shows no real credentials.

Required variables: `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, `RABBITMQ_DEFAULT_USER`,
`RABBITMQ_DEFAULT_PASS`, `GRAFANA_PASSWORD`, `SILICONFLOW_API_KEY`.
Optional (with defaults): `WEB_PORT` (80), `SEATFLOW_SEAT_GATE_ENABLED` (true).

Service passwords must match the values their data volumes were initialised with;
changing them on an existing volume requires re-initialising that volume.

## API

All responses use a single envelope `{ code, message, data }` (`code = 0` on success).
Authenticated endpoints expect `Authorization: Bearer <token>`.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/health` | Liveness and build timestamp |
| GET | `/actuator/health` | Actuator health, including database connectivity |
| POST | `/api/auth/register` | Create a participant account |
| POST | `/api/auth/login` | Authenticate, returns a JWT |
| GET | `/api/activities` | List published activities ordered by registration start |
| GET | `/api/activities/{id}` | Single activity, 404 when absent |
| POST | `/api/activities` | Create a draft activity (organizer) |
| PUT | `/api/activities/{id}` | Update a draft activity |
| POST | `/api/activities/{id}/publish` | Publish a draft |
| POST | `/api/activities/{id}/cancel` | Cancel an activity |
| GET | `/api/activities/my` | The caller's unpublished drafts |
| GET | `/api/activities/{id}/registrations` | Registration roster (organizer) |
| POST | `/api/registration/{activityId}/register` | Register, holds a seat (PENDING) |
| POST | `/api/registration/{activityId}/confirm` | Confirm within the TTL (CONFIRMED) |
| POST | `/api/registration/{activityId}/cancel` | Cancel, releases the seat |
| GET | `/api/registration/mine` | The caller's registrations |
| GET | `/api/notifications/mine` | The caller's notifications |
| POST | `/api/qa` | Retrieval-augmented question answering, returns citations |
| POST | `/api/qa/reindex` | Rebuild the knowledge index (admin) |
| GET | `/api/admin/users` | Fuzzy user search (admin) |
| PATCH | `/api/admin/users/{userId}/role` | Assign a role (admin) |

## Frontend

`../seatflow-web` is a Vue 3 + TypeScript SPA. Views map to the roles above:
activity plaza and detail, login / register, my registrations (with a confirm
countdown), notifications, an organizer console (create / publish / roster + CSV
export), an admin console (user search + role assignment), and a RAG Q&A page.

| Script | Purpose |
| --- | --- |
| `npm run dev` | Vite dev server with API proxy |
| `npm run build` | Type-checked production build to `dist/` |
| `npm run typecheck` | `vue-tsc --noEmit` |
| `npm run preview` | Preview the production build |

The production image (`seatflow-web/Dockerfile`) builds with Node 22 and serves the
static bundle through nginx (`seatflow-web/nginx.conf`), which proxies `/api` and
`/actuator` to the `app` service and falls back to `index.html` for SPA routes.

## Project layout

Backend — one package per feature, not per technical layer:

```
src/main/java/com/seatflow
├── SeatFlowApplication.java
├── common          ApiResponse, BusinessException, GlobalExceptionHandler
├── config          clock, JPA auditing, scheduling, web MVC, properties
├── security        JWT filter / token service, security config, entry points
├── user            auth, accounts, roles, admin user management
├── activity        activity entity, service, controller, indexing
├── registration    registration state machine and seat handling
├── notification    outbox dispatcher, consumer, controller
├── qa              RAG ingestion, retrieval, Q&A controller
├── infra           redis seat gate, rate limit, messaging, scheduling
├── metrics         Micrometer custom metrics
└── health          liveness endpoint
```

Frontend:

```
../seatflow-web/src
├── api             typed Axios clients per feature
├── stores          Pinia auth store
├── router          role-guarded routes
├── layouts         MainLayout (role-aware menu)
├── views           one view per page
├── components      shared UI (e.g. SeatProgress)
└── composables     useCountdown
```

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

## Observability

The app exposes Micrometer metrics through Actuator; Prometheus scrapes them
(`docker/prometheus/prometheus.yml`) and Grafana ships provisioned dashboards
(`docker/grafana`). Custom metrics cover seat-gate decisions and the notification
outbox. Open Grafana at http://localhost:3000.

## Roadmap

| Milestone | Scope | Status |
| --- | --- | --- |
| M1 | Skeleton: compose, Flyway, layering, health endpoint, README | done |
| M2 | Registration and authentication, validation, role-based access | done |
| M3 | Seat consistency: Redis atomic gate, idempotency, rate limiting | done |
| M4 | Async notifications (outbox), container images, compose delivery | done |
| M5 | Metrics, Prometheus + Grafana observability | done |
| M5.5 | Retrieval augmented Q&A assistant | done |
| — | Vue 3 frontend + nginx containerised delivery | done |
| M6 | English documentation, architecture diagram, interview write-up | in progress |

## Licence

Personal learning project.