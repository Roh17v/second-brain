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
| `DB_URL` | `jdbc:postgresql://localhost:5432/secondbrain?user=postgres&password=your_password` | Yes |
| `SERVER_PORT` | `8080` | No (default 8080) |

Use a **single** JDBC URL that includes host, port, database, user, and password.

Correct (JDBC):

```text
jdbc:postgresql://localhost:5432/secondbrain?user=postgres&password=your_password
```

Incorrect for Spring/JDBC (Node/Prisma style — will not work as-is):

```text
postgresql://postgres:your_password@localhost:5432/secondbrain
```

Never commit real passwords. Do not put secrets in `application.yml`.

### Profiles

| Profile | When | Notes |
|---------|------|--------|
| `dev` (default) | Local development | Verbose logging, `ddl-auto: update` |
| `prod` | Production | Stricter logging, `ddl-auto: validate` |

## Run (local)

### IntelliJ

Run configuration → **Environment variables**:

```text
DB_URL=jdbc:postgresql://localhost:5432/secondbrain?user=postgres&password=your_password
```

### PowerShell

```powershell
cd backend

$env:DB_URL="jdbc:postgresql://localhost:5432/secondbrain?user=postgres&password=your_password"

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

## Auth + Users

Main class: `com.secondbrain.SecondBrainApplication`

Required env vars: `DB_URL`, `JWT_SECRET` (32+ characters).

### Register

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/register `
  -ContentType "application/json" `
  -Body '{"email":"rohit@example.com","name":"Rohit","password":"secret123"}'
```

### Login

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"email":"rohit@example.com","password":"secret123"}'
```

Both return `accessToken`. Use it for protected routes:

```powershell
$token = "paste-token-here"
Invoke-RestMethod -Uri http://localhost:8080/api/users `
  -Headers @{ Authorization = "Bearer $token" }
```

| Endpoint | Auth |
|----------|------|
| `GET /api/health` | Public |
| `POST /api/auth/register` | Public |
| `POST /api/auth/login` | Public |
| `GET /api/users` | Bearer JWT |
| `GET /api/users/{id}` | Bearer JWT |

If you previously created users without `password_hash`, drop the `users` table once (dev only) so Hibernate can recreate it.

## Build

```powershell
.\mvnw.cmd clean package
```

## Tests

Tests use an in-memory H2 database (`test` profile) and do not need Postgres.

```powershell
.\mvnw.cmd test
```
