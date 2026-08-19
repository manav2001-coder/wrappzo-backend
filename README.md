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
