# RaceRecall Backend

REST API for **RaceRecall**, an app for looking back at Formula 1 sessions. It fetches race data from the public [OpenF1 API](https://openf1.org), combines it into one response per session (podium, starting grid, weather) and handles user registration and login with JWT.

**Live app (frontend):** https://racerecall-frontend.vercel.app/

## Tech stack

- Java 17, Spring Boot 4.1 (Web MVC, WebFlux `WebClient`, Security, Data JPA, Cache)
- SQLite by default (a single file, no setup), or PostgreSQL with the `postgres` profile
- JWT authentication (jjwt) with BCrypt password hashing
- Maven (wrapper included) and Docker

## API

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/sessions?year=2026&page=0&size=10` | public | Sessions for a year, paginated by grand prix weekend |
| `GET` | `/api/sessions/{sessionKey}/details` | public | Session info, podium, starting grid and weather |
| `POST` | `/api/auth/register` | public | Create an account, returns a JWT |
| `POST` | `/api/auth/login` | public | Log in, returns a JWT |


## Running locally

### Prerequisites

- JDK 17 or newer

No database setup is needed: by default the app uses SQLite and creates `racerecall.db` in the project folder on first start (it is git-ignored). Tables are created automatically (`ddl-auto=update`).

### 1. Create a `.env` file

In the project root, create a file named `.env` (it is already git-ignored):

```env
JWT_SECRET=replace-with-a-long-random-string-of-at-least-32-characters
```

You can generate a secret with `openssl rand -base64 48`.

### 2. Run the app

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. The API is now at http://localhost:8080. Quick check:

```bash
curl "http://localhost:8080/api/sessions?year=2025&size=2"
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

## Project structure

```
src/main/java/com/racerecall/backend/
├── clients/       OpenF1Client: calls the OpenF1 API (with retry on 429 and caching)
├── config/        CORS configuration
├── controllers/   AuthController, SessionController
├── entities/      User (JPA entity)
├── models/        DTO records and auth request/response classes
├── repositories/  UserRepository
└── services/      SessionService, AuthService, CleanupService, JWT and security classes

src/main/resources/
├── application.properties           Shared settings; picks the database profile
├── application-sqlite.properties    SQLite (default)
└── application-postgres.properties  PostgreSQL, from environment variables
```

## Notes

- OpenF1 responses are cached in memory, so a restart clears the cache.
- Accounts are temporary: `CleanupService` deletes every account older than 7 days, daily at 03:00 server time.
- Startup can take a couple of minutes on a small VM; the API doesn't answer until the log shows `Started RacerecallBackendApplication`.
- Session endpoints are public for now. Only unlisted routes require a valid `Authorization: Bearer <token>` header.