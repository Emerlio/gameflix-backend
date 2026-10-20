package com.gameflix.auth.auth;

/**
 * Successful login response. The token is a signed JWT; {@code expiresInSeconds}
 * reflects the {@code exp} claim baked into that token.
 */
public record AuthResponse(String token, String tokenType, long expiresInSeconds) {

    public static AuthResponse bearer(String token, long expiresInSeconds) {
        return new AuthResponse(token, "Bearer", expiresInSeconds);
    }
}
