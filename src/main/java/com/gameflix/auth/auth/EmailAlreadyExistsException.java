package com.gameflix.auth.auth;

/** Thrown when a registration uses an email that is already taken (-> HTTP 409). */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("email already registered");
    }
}
