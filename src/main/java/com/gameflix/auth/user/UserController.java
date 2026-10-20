package com.gameflix.auth.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Protected user endpoints. Reaching any method here means the JWT filter has
 * already authenticated the request; an invalid, expired, or missing token is
 * rejected with 401 before the controller is invoked.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the caller's own profile, identified from the authenticated
     * principal (the user id carried in the token's subject claim).
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(UserProfileResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }
}
