package com.dnastack.ddap.cli.client.ddap;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
