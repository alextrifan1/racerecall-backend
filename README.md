# RaceRecall Backend

REST API for **RaceRecall**, an app for looking back at Formula 1 sessions. It fetches race data from the public [OpenF1 API](https://openf1.org), combines it into one response per session (podium, starting grid, weather) and handles user registration and login with JWT.

**Live app (frontend):** https://racerecall-frontend.vercel.app/

## Tech stack

- Java 17, Spring Boot 4.1 (Web MVC, WebFlux `WebClient`, Security, Data JPA, Cache)
- PostgreSQL (hosted on Neon in production)
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
- A PostgreSQL database (a local one in Docker is the easiest option, see step 1)

### 1. Start a database

```bash
docker run --name racerecall-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=racerecall -p 5432:5432 -d postgres:16
```

Tables are created automatically on first start (`ddl-auto=update`).

### 2. Create a `.env` file

In the project root, create a file named `.env` (it is already git-ignored):

```env
JWT_SECRET=replace-with-a-long-random-string-of-at-least-32-characters
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/racerecall
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
```

You can generate a secret with `openssl rand -base64 48`.

### 3. Run the app

```bash
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd spring-boot:run`. The API is now at http://localhost:8080. Quick check:

```bash
curl "http://localhost:8080/api/sessions?year=2025&size=2"
```

### Run with Docker instead

```bash
docker build -t racerecall-backend .
docker run -p 8080:8080 --env-file .env racerecall-backend
```

## Configuration

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `JWT_SECRET` | yes | none | Key used to sign tokens (32+ characters) |
| `SPRING_DATASOURCE_URL` | yes | none | JDBC URL of the Postgres database |
| `SPRING_DATASOURCE_USERNAME` | yes | none | Database user |
| `SPRING_DATASOURCE_PASSWORD` | yes | none | Database password |
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
└── services/      SessionService, AuthService, JWT and security classes
```

## Notes

- OpenF1 responses are cached in memory, so a restart clears the cache.
- Session endpoints are public for now. Only unlisted routes require a valid `Authorization: Bearer <token>` header.