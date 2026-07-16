# Second Brain — Backend

Spring Boot API for the Second Brain personal knowledge assistant.

## Requirements

- JDK 21
- PostgreSQL with a database (e.g. `secondbrain`)
- Maven Wrapper (`mvnw` / `mvnw.cmd`) — no global Maven required

## Configuration

Secrets and environment-specific values come from **environment variables**.
See [`.env.example`](.env.example) for the full list.

| Variable | Example | Required |
|----------|---------|----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/secondbrain` | Yes |
| `DB_USERNAME` | `postgres` | Yes |
| `DB_PASSWORD` | *(your password)* | Yes |
| `SERVER_PORT` | `8080` | No (default 8080) |

Never commit real passwords. Do not put secrets in `application.yml`.

### Profiles

| Profile | When | Notes |
|---------|------|--------|
| `dev` (default) | Local development | Verbose logging, `ddl-auto: update` |
| `prod` | Production | Stricter logging, `ddl-auto: validate` |

## Run (local)

```powershell
cd backend

$env:DB_URL="jdbc:postgresql://localhost:5432/secondbrain"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your_password"

.\mvnw.cmd spring-boot:run
```

Health check:

```text
GET http://localhost:8080/api/health
```

Expected when DB is up:

```json
{ "status": "ok", "database": "up" }
```

## Build

```powershell
.\mvnw.cmd clean package
```

## Tests

Tests use an in-memory H2 database (`test` profile) and do not need Postgres.

```powershell
.\mvnw.cmd test
```
