package com.gameflix.auth.auth;

import com.gameflix.auth.security.JwtService;
import com.gameflix.auth.user.User;
import com.gameflix.auth.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Registers a new user. The password is hashed before persisting. Uniqueness
     * is checked up front and also enforced by the database constraint, so a race
     * between two concurrent sign-ups still results in a 409 rather than a
     * duplicate row.
     */
    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User(request.email(), passwordEncoder.encode(request.password()));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint tripped by a concurrent insert.
            throw new EmailAlreadyExistsException();
        }
    }

    /**
     * Verifies credentials and issues a signed JWT. Any failure (unknown email or
     * wrong password) throws the same generic error so the response never reveals
     * which emails are registered.
     */
    @Transactional(readOnly = true)
    public TokenResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid email or password");
        }

        String token = jwtService.generateToken(user.getId());
        return new TokenResult(token, jwtService.getExpirationSeconds());
    }

    public record TokenResult(String token, long expiresInSeconds) {
    }
}
