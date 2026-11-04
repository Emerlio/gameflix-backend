# GameFlix Auth Service

A Spring Boot service that registers users, authenticates them, issues a signed
**JWT**, and uses that token to protect a route. Users are persisted in **MySQL**.
This is the Module 4 build for the GameFlix project.

- **Java:** 21 &nbsp;·&nbsp; **Spring Boot:** 3.5.16 (course baseline) &nbsp;·&nbsp; **DB:** MySQL 8
- **Security:** Spring Security 6 (lambda `SecurityFilterChain`), BCrypt via the delegating
  password encoder, HS256 JWT.

---

## Endpoints

| Method & path | Behavior | Success / failure codes |
|---|---|---|
| `POST /api/auth/register` | Creates a user. Hashes the password before persisting. Never returns the hash. | `201` created · `400` invalid input · `409` email already registered |
| `POST /api/auth/login` | Verifies credentials, returns a signed JWT with an `exp` claim. | `200` with token · `401` on any credential failure |
| `GET /api/users/me` | Protected. Returns the caller's own profile, read from the token's subject claim. | `200` with profile · `401` missing / invalid / expired / tampered token |

---

## How to run it

### 1. Prerequisites
- JDK 21 on the `PATH` (or `JAVA_HOME` set to it)
- A running MySQL 8 server on `localhost:3306`

### 2. Create the database and app user
Run this once as a MySQL admin (in MySQL Workbench or the CLI). A copy with a real password
is kept locally as `db-setup.local.sql` (git-ignored); use the snippet below as the template:

```sql
CREATE DATABASE IF NOT EXISTS gameflix_auth
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'gameflix_app'@'%' IDENTIFIED BY '<choose-a-password>';
GRANT ALL PRIVILEGES ON gameflix_auth.* TO 'gameflix_app'@'%';
FLUSH PRIVILEGES;
```

The table itself is created automatically by Hibernate on first run; `schema.sql` is
committed so the column types and constraints are visible without running the app.

### 3. Provide configuration (no secrets in git)
Copy `src/main/resources/application.properties.example` to
`src/main/resources/application-local.properties` (git-ignored) and fill in your DB user,
DB password, and a JWT secret of **at least 32 characters** — or supply the same values as
environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`,
`JWT_EXP_MINUTES`, `CORS_ORIGIN`). The committed `application.properties` only references
these; it contains no credentials.

### 4. Build and run
```bash
# run the tests (they use in-memory H2, no MySQL needed)
./mvnw test

# run the service against MySQL using the local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# or, from the built jar:
java -jar target/auth-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```
The service listens on `http://localhost:8080`.

---

## Schema

```sql
CREATE TABLE `users` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `created_at` DATETIME(6)  NOT NULL,
  `email`      VARCHAR(320) NOT NULL,
  `password`   VARCHAR(255) NOT NULL,   -- stores the BCrypt hash, NOT the password
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Design decisions (and why)

### Password hashing — BCrypt via the delegating encoder
Passwords are hashed with **BCrypt at strength 10** (the Spring default), using
`PasswordEncoderFactories.createDelegatingPasswordEncoder()`. BCrypt is an adaptive
algorithm on the OWASP Password Storage list; strength 10 means 2¹⁰ key-expansion rounds,
at or above the commonly cited minimum, and is tunable upward later. The delegating encoder
stores the algorithm as a prefix — `{bcrypt}$2a$10$...` — so each hash records how it was
produced and the app can migrate algorithms later while still verifying old hashes. Because
the prefixed hash is **68 characters**, the `password` column is `VARCHAR(255)`, not
`VARCHAR(60)`, so nothing is silently truncated. Registration also caps the raw password at
64 characters, keeping it under BCrypt's 72-byte input limit.

### JWT — HS256, short-lived, secret externalized
Login returns a JWT signed with **HMAC-SHA256**. The signing secret is read from
configuration/environment and is **never** hardcoded in a committed file. Each token carries
an `exp` claim; the lifetime is **60 minutes** — long enough that a user is not re-logging in
constantly, short enough that a leaked token stops working quickly (there is no server-side
session to revoke, so a short lifetime is the main mitigation). The payload contains **only
the subject (user id)** — no password, email, or other confidential data — because a JWT
payload is Base64URL-encoded, not encrypted, so anyone holding the token can read every claim.
Expired or tampered tokens fail signature/expiration verification server-side and the
protected route returns `401`.

### `spring.jpa.hibernate.ddl-auto = update`
`update` evolves the schema to match the entities **without wiping data between runs**, so
registered users (and the password-hash evidence) survive a restart. `create-drop` would
erase the table on every shutdown, making test evidence harder to reproduce.

### Other security choices
- **Generic login failure.** Both "unknown email" and "wrong password" return the same
  `401 {"error":"invalid email or password"}`, so the response never reveals which emails
  are registered.
- **Email uniqueness at the database level** via a `UNIQUE` constraint, not only an
  application check, so two concurrent sign-ups cannot both succeed; the `409` is driven by
  the constraint.
- **CORS** is restricted to a single configured front-end origin (`app.cors.allowed-origin`),
  never `*`.
- **Stateless**: no HTTP session; CSRF is disabled because there is no session cookie to
  protect.

---

## Module 3 design-change note
The Module 3 ERD modelled `users` with `user_id, email, username, password_hash, status,
join_date`. For this Module 4 authentication service I kept the auth-relevant columns and
made three deliberate changes: the hash column is named **`password`** (it stores the hash;
the name matches the grading `SELECT`), `join_date` became **`created_at`**, and `username`
and `status` are **omitted** from the MVP because registering, logging in, and protecting a
route do not need them. They can be added back as the User service grows without affecting
the auth flow.

---

## Test evidence
Both the success and failure path of every endpoint are covered.

- **Automated:** `@SpringBootTest` + `MockMvc` (`AuthEndpointsTest`), run with `./mvnw test`
  — **9/9 passing** against in-memory H2. Covers: successful registration; duplicate-email
  rejection (`409`); invalid input (`400`); successful login returning a token; login with a
  wrong password (`401`); `/me` with a valid token (`200`); `/me` with no token (`401`); and
  `/me` with a tampered token (`401`).
- **Live run against MySQL:** the three endpoints were also exercised with `curl` against the
  running MySQL-backed service, and `SELECT id, email, password FROM users;` confirms the
  stored value is a BCrypt hash, not the password.

Screenshots of the working output and the database query are in the accompanying Word
document.

---

## Use of AI assistance
I used an AI assistant (Claude) to help scaffold the project and draft the Spring Boot code,
then reviewed and tested all of it. One concrete thing I changed: an early version used a
bare `new BCryptPasswordEncoder()` and a `VARCHAR(60)` password column. I replaced it with
the **delegating** encoder and widened the column to `VARCHAR(255)`, because the delegating
encoder stores a `{bcrypt}`-prefixed 68-character hash that a 60-char column would silently
truncate — every login would then fail with nothing in the stack trace to explain why. I
also trimmed the JWT payload down to just the subject after an initial draft included the
email as a claim, since the payload is only encoded, not encrypted, and should carry nothing
beyond what the server needs to identify the caller.

---

## Project layout
```
src/main/java/com/gameflix/auth/
  AuthServiceApplication.java
  auth/      AuthController, AuthService, RegisterRequest, LoginRequest, AuthResponse, EmailAlreadyExistsException
  user/      User, UserRepository, UserController, UserProfileResponse
  security/  SecurityConfig, JwtService, JwtAuthenticationFilter
  web/       GlobalExceptionHandler
src/main/resources/
  application.properties            (env-driven, committed, no secrets)
  application.properties.example    (template, committed)
  application-local.properties      (real secrets, git-ignored)
src/test/java/com/gameflix/auth/    AuthEndpointsTest, AuthServiceApplicationTests
src/test/resources/application.properties   (H2 test profile)
schema.sql
```

## Continuous Integration (GitHub Actions)

`.github/workflows/ci.yml` runs on every push and pull request to `main`:

1. Checks out the repository
2. Sets up **JDK 21** (Temurin) — this project targets Java 21, not 17
3. `mvn clean package` — compiles and runs the tests (against in-memory H2)
4. `docker build -t gameflix-backend .` — builds the container image from the `Dockerfile`

### Run the container locally
```bash
docker build -t gameflix-backend .
docker run -p 8080:8080 \
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/gameflix_auth" \
  -e DB_USERNAME=gameflix_app -e DB_PASSWORD=*** \
  -e JWT_SECRET=*** \
  gameflix-backend
```
