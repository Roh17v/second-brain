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
| `JWT_SECRET` | long random string (32+) | Yes |
| `SERVER_PORT` | `8080` | No (default 8080) |
| `STORAGE_PROVIDER` | `local` \| `b2` \| `s3` | No (default `local`) |

### File storage

| Provider | When | Config |
|----------|------|--------|
| `local` | Dev default | `FILE_STORAGE_PATH` (default `./storage`) |
| `b2` | Backblaze B2 | `STORAGE_BUCKET`, `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` |
| `s3` | AWS / MinIO / R2 | Same `STORAGE_*` vars as B2 |

Optional: `STORAGE_KEY_PREFIX` (e.g. `secondbrain/`), `STORAGE_PATH_STYLE=true` (recommended for B2).

DB rows store a **relative key** only; switch provider without rewriting document metadata (re-upload or migrate bytes separately).

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

### Continue with Google

Set `GOOGLE_CLIENT_ID` (OAuth 2.0 **Web** client ID). The SPA sends a GIS ID token:

```powershell
Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/google `
  -ContentType "application/json" `
  -Body '{"idToken":"<google-id-token>"}'
```

If the Google email already has a password account, Google is **linked** to that user (email marked verified; password kept). Google-only accounts have no password and must use Continue with Google.

Both password and Google return `accessToken`. Use it for protected routes:

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
| `POST /api/auth/google` | Public (Google ID token) |
| `POST /api/auth/verify-email` | Public |
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
