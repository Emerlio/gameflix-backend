-- GameFlix auth schema (MySQL 8).
-- Hibernate (ddl-auto=update) creates this automatically on first run; it is also
-- committed here so the column types and constraints are visible without running the app.

CREATE DATABASE IF NOT EXISTS gameflix_auth
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE gameflix_auth;

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(320) NOT NULL,
    password   VARCHAR(255) NOT NULL,   -- stores the BCrypt hash, NOT the raw password
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
