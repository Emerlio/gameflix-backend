package com.gameflix.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.security.Keys;

/**
 * Creates and verifies the signed JWTs used to authenticate API calls.
 *
 * <p>Tokens are signed with HMAC-SHA256 using a secret read from configuration
 * (never hardcoded). The only claim is the subject = user id; no password, email,
 * or other confidential data is placed in the payload, because a JWT payload is
 * only Base64URL-encoded, not encrypted. Every token carries an {@code exp} claim
 * and expired or tampered tokens fail verification server-side.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        // hmacShaKeyFor rejects a secret shorter than 256 bits, so a weak secret fails fast at startup.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and expiration, then returns the user id from the
     * subject claim. Throws {@link io.jsonwebtoken.JwtException} if the token is
     * invalid, tampered, or expired.
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(claims.getSubject());
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
