package com.rentmanager.modules.reservation.infrastructure.daraja;

public class DarajaException extends RuntimeException {

    public DarajaException(String message) {
        super(message);
    }

    public DarajaException(String message, Throwable cause) {
        super(message, cause);
    }
}