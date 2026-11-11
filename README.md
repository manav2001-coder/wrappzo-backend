# wrappzo-backend
Backend repository for wrapzzo (Spring Boot)

## Stack

- Java 21
- Spring Boot 4.1 (Web, Data JPA, Validation)
- PostgreSQL
- Flyway (migrations in `src/main/resources/db/migration`)
- Maven

## Local development

Start a local PostgreSQL instance:

```
docker compose up -d
```

Run the app:

```
./mvnw spring-boot:run
```

The default datasource config (`src/main/resources/application.properties`) points at `localhost:5432/wrapzzo`
with user/password `wrapzzo`/`wrapzzo`, matching `docker-compose.yml`. Override via the `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` environment variables for other environments.

## Project layout

- `controller` — REST controllers
- `service` — business logic
- `repository` — Spring Data JPA repositories
- `model` — JPA entities
- `config` — Spring configuration classes
- `resources/db/migration` — Flyway migration scripts (`V1__*.sql`, `V2__*.sql`, ...)

## Monitoring

- **Uptime check (public):** `GET /api/public/health` -- 200 `{"status":"UP"}` when the app reaches its database, 503 otherwise. Point Railway's healthcheck or an uptime monitor at it.
- **Metrics (private):** Prometheus format at `/actuator/prometheus` on the **management port** (`MANAGEMENT_PORT`, default 9090). Railway routes only its public domain to the app's own port, so this is reachable from inside the project's private network only (`http://<service>.railway.internal:9090/actuator/prometheus`). Never set `MANAGEMENT_PORT` to the app's own port. Liveness and readiness probes are at `/actuator/health/liveness` and `/readiness` on the same port.
- **What is in it:** `http_server_requests_seconds_*` (rate, latency buckets and errors, by endpoint and status), `hikaricp_connections_*` (pool in use / waiting / max), JVM and Tomcat, and the app's own queues: `wrapzzo_email_outbox_pending`, `_failed`, `_oldest_due_seconds` (grows when sending is stuck) and `wrapzzo_import_jobs_open`.
- **Suggested alerts:** 5xx rate above 1% for 5 min; p95 latency of any endpoint above 2 s for 5 min; `hikaricp_connections_pending > 0` for 2 min (pool too small); `wrapzzo_email_outbox_oldest_due_seconds > 600`; `wrapzzo_email_outbox_failed > 0`.
- **In the logs:** `Slow request: GET /api/... took N ms (status S)` for any request over `SLOW_REQUEST_MS` (1500), and Hibernate's `org.hibernate.SQL_SLOW` for any SQL over `SLOW_QUERY_MS` (500). Connections held over 30 s are logged by the pool (`DB_POOL_LEAK_THRESHOLD_MS`).
