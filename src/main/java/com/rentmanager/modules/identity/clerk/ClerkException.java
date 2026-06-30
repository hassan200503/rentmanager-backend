package com.rentmanager.modules.identity.clerk;

public class ClerkException extends RuntimeException {

    public ClerkException(String message) {
        super(message);
    }

    public ClerkException(String message, Throwable cause) {
        super(message, cause);
    }
}