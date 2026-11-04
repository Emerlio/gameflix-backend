# Running GameFlix Backend with Docker + MySQL

This runs the full stack — the Spring Boot backend **and** a MySQL database — as two
containers wired together with Docker Compose.

## What's in the setup
| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage build: compiles the jar with Maven (JDK 21), then runs it on a small JRE image. |
| `docker-compose.yml` | Defines two services — `mysql` (MySQL 8) and `app` (this backend) — on one network. |
| `.env.example` | Template for the environment variables; copy to `.env`. |

The `app` container reaches the database at the hostname **`mysql`** (the compose service
name), not `localhost`, because each container has its own network namespace.

## How it's wired
- The `mysql` service creates the `gameflix_auth` database and the `gameflix_app` user on
  first start (from `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD`).
- The `app` service waits for MySQL to be **healthy** (`depends_on: condition: service_healthy`)
  before starting, so it never races the database.
- The app reads its DB URL, username, password, and JWT secret from environment variables
  (set in `docker-compose.yml`), which override the defaults in `application.properties`.
- MySQL data persists in the named volume `mysql_data` across restarts.

## Prerequisites
- Docker Desktop (with Docker Compose v2) installed and running.

## Build and run
```bash
# 1. Provide environment values
cp .env.example .env          # Windows PowerShell: copy .env.example .env

# 2. Build the image and start both containers
docker compose up --build
```
The first run downloads the base images and builds the jar, so give it a few minutes.
When it's ready you'll see `Started AuthServiceApplication` in the logs and both containers
running.

Run it in the background instead with:
```bash
docker compose up --build -d
docker compose ps        # see both services
docker compose logs -f app
```

## Test the running stack
With the stack up, the backend is on `http://localhost:8080`:
```bash
# Register a user
curl -i -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@gameflix.test","password":"Throwaway!23"}'

# Log in (returns a JWT)
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@gameflix.test","password":"Throwaway!23"}'

# Call the protected route with the token from the login response
curl -i http://localhost:8080/api/users/me -H "Authorization: Bearer <token>"
```
Confirm the data landed in the containerized MySQL:
```bash
docker compose exec mysql mysql -ugameflix_app -pgameflix_pass gameflix_auth \
  -e "SELECT id, email, password FROM users;"
```

## Stop and clean up
```bash
docker compose down            # stop and remove the containers
docker compose down -v         # also delete the MySQL data volume (fresh DB next time)
```

## Troubleshooting
- **App exits with "Public Key Retrieval is not allowed":** the JDBC URL already includes
  `allowPublicKeyRetrieval=true` for MySQL 8's `caching_sha2_password` auth over a non-TLS
  link — keep that parameter.
- **App starts before the DB is ready:** handled by the health check + `depends_on`; if you
  remove them, the app may need a restart on the very first boot.
- **Port already in use:** stop any local MySQL on 3306 or the app on 8080, or change the
  left-hand side of the `ports` mapping.
