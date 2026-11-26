# RaceRecall Backend

![CI](https://github.com/alextrifan1/racerecall-backend/actions/workflows/ci.yml/badge.svg)

REST API for **RaceRecall**, an app for looking back at Formula 1 sessions. It fetches race data from the public [OpenF1 API](https://openf1.org), combines it into one response per session (podium, starting grid, weather) and handles user registration and login with JWT.

**Live app (frontend):** https://racerecall-frontend.vercel.app/

## Tech stack

- Java 21, Spring Boot 4.2 (Web MVC, WebFlux `WebClient`, Security, Data JPA, Cache, Validation, Actuator)
- SQLite by default (a single file, no setup), or PostgreSQL with the `postgres` profile
- JWT authentication (jjwt) with BCrypt password hashing
- JUnit 5, AssertJ, Mockito, MockMvc, H2 for tests
- Maven (wrapper included), Docker, GitHub Actions

## API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/sessions?year=2026&page=0&size=10` | public | Sessions for a year, paginated by grand prix weekend |
| `GET` | `/api/sessions/{sessionKey}/details` | public | Session info, podium, starting grid and weather |
| `POST` | `/api/auth/register` | public | Create an account, returns a JWT |
| `POST` | `/api/auth/login` | public | Log in, returns a JWT |
| `GET` | `/actuator/health` | public | Overall health (`UP` / `DOWN`) |
| `GET` | `/actuator/health/liveness` | public | Is the process alive |
| `GET` | `/actuator/health/readiness` | public | Can it serve requests (includes the database) |

### Validation

- `page` must be `>= 0`, `size` between `1` and `100`
- Register: username 3–32 characters, password 8–72 characters (BCrypt uses at most 72 bytes)

### Errors

Errors use the standard [Problem Details](https://www.rfc-editor.org/rfc/rfc9457) format:

```json
{
  "type": "about:blank",
  "title": "Session not found",
  "status": 404,
  "detail": "No session found with key 9999",
  "instance": "/api/sessions/9999/details"
}
```

| Status | When |
|--------|------|
| `400` | Invalid parameters or request body (validation errors include an `errors` map per field) |
| `401` | Wrong username or password on login |
| `403` | Protected route without a valid token |
| `404` | Unknown session |
| `409` | Username already taken |
| `502` | OpenF1 is unavailable |

## Running locally

### Prerequisites

- JDK 21

No database setup is needed: by default the app uses SQLite and creates `racerecall.db` in the project folder on first start (it is git-ignored). Tables are created automatically (`ddl-auto=update`).

### 1. Create a `.env` file

In the project root, create a file named `.env` (it is already git-ignored):

```env
JWT_SECRET=replace-with-a-long-random-string-of-at-least-32-characters
```

You can generate a secret with `openssl rand -base64 48`. The app refuses to start if the secret is shorter than 32 characters.

### 2. Run the app

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. The API is now at http://localhost:8080. Quick check:

```bash
curl "http://localhost:8080/api/sessions?year=2025&size=2"
curl http://localhost:8080/actuator/health
```

### Run with Docker instead

Mount a folder for the database file, otherwise it is lost when the container is removed:

```bash
docker build -t racerecall-backend .
docker run -p 8080:8080 --env-file .env \
  -e SQLITE_DB_PATH=/app/data/racerecall.db \
  -v "$(pwd)/data:/app/data" \
  racerecall-backend
```

The image has a `HEALTHCHECK` on `/actuator/health/readiness`, so `docker ps` shows `healthy` once the app is ready (startup can take 1–2 minutes on a small VM).

### Using PostgreSQL instead

Switch to the `postgres` profile and give it the connection details, for example in `.env`:

```env
SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/racerecall
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

For a local Postgres, the quickest option is Docker:

```bash
docker run --name racerecall-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=racerecall -p 5432:5432 -d postgres:16
```

## Configuration

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `JWT_SECRET` | yes | none | Key used to sign tokens (32+ characters) |
| `SPRING_PROFILES_ACTIVE` | no | `sqlite` | Database to use: `sqlite` or `postgres` |
| `SQLITE_DB_PATH` | no | `./racerecall.db` | Where the SQLite file is stored (`sqlite` profile) |
| `SPRING_DATASOURCE_URL` | with `postgres` | none | JDBC URL of the Postgres database |
| `SPRING_DATASOURCE_USERNAME` | with `postgres` | none | Database user |
| `SPRING_DATASOURCE_PASSWORD` | with `postgres` | none | Database password |
| `PORT` | no | `8080` | Port the server listens on |
| `FRONTEND_URL` | no | `http://localhost:3000,http://localhost:5173` | Allowed CORS origins, comma-separated |

## Tests

```bash
./mvnw test
```

Tests run with the `test` profile: an in-memory H2 database and a dummy JWT secret, so no `.env` or external services are needed. OpenF1 is replaced by a fake HTTP layer, so the tests never call the real API.

- **Unit tests:** `JwtService`, `SessionService`, `AuthService` (JUnit 5, Mockito)
- **Web layer:** controllers, security rules, CORS and error responses (`@WebMvcTest`, MockMvc)
- **HTTP client:** JSON mapping, retry on 429, error handling and caching of `OpenF1Client`
- **Full application:** context startup and health endpoints (`@SpringBootTest`)

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`):

1. **Build and test:** `./mvnw verify` on every pull request and every push to `main`
2. **Docker image and health check:** builds the image, starts it and waits for `/actuator/health/readiness`
3. **Deploy to production:** only on `main`, only after both jobs pass. Connects over SSH with a deploy-only key and runs the deploy script on the server (Docker Compose rebuild + readiness check)

Also enabled: Dependabot (Maven, GitHub Actions, Docker images), CodeQL code scanning, and branch protection on `main` (pull request and passing checks required).

## Project structure

```
src/main/java/com/racerecall/backend/
├── clients/       OpenF1Client: calls the OpenF1 API (retry on 429, caching, error handling)
├── config/        CORS configuration
├── controllers/   AuthController, SessionController
├── entities/      User (JPA entity)
├── exceptions/    Custom exceptions and GlobalExceptionHandler (Problem Details responses)
├── models/        DTO records and auth request/response classes
├── repositories/  UserRepository
├── security/      SecurityConfig, JwtAuthenticationFilter, JwtService, CustomUserDetailsService
└── services/      SessionService, AuthService, CleanupService

src/main/resources/
├── application.properties           Shared settings, Actuator, database profile
├── application-sqlite.properties    SQLite (default)
└── application-postgres.properties  PostgreSQL, from environment variables

src/test/resources/
└── application-test.properties      H2 in memory + dummy JWT secret
```

## Notes

- OpenF1 responses are cached in memory, so a restart clears the cache. Failed requests are **not** cached; a 404 from OpenF1 means "no data" (for example, no starting grid for a practice session).
- If OpenF1 is down, the API returns `502` instead of empty data.
- Invalid or expired tokens are ignored: public endpoints still work, protected ones are rejected.
- Accounts are temporary: `CleanupService` deletes every account older than 7 days, daily at 03:00 server time.

## Contributing

- One branch per issue (`fix/12-...`, `feat/...`), pull request into `main`
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `test:`, `ci:`, `chore:`...)
- The pull request template asks for Problem / Fix / Tests; link the issue with `Fixes #<number>`