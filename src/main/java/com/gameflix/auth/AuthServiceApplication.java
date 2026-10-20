package com.gameflix.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// This service authenticates manually (password hash check + JWT), so the default
// in-memory UserDetailsService is excluded: no stray generated login password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
