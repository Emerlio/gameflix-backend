package com.gameflix.auth.user;

import java.time.Instant;

/**
 * Public view of a user. Deliberately omits the password hash so it can never
 * be returned by an endpoint.
 */
public record UserProfileResponse(Long id, String email, Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
