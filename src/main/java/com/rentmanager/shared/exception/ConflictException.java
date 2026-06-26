// com/rentmanager/shared/exception/ConflictException.java
package com.rentmanager.shared.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}