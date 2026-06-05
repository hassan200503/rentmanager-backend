package com.rentmanager.shared.exception;

import com.rentmanager.shared.exception.ErrorCode;

import java.util.UUID;

public class PropertyNotFoundException extends RuntimeException {

    private final ErrorCode errorCode;
    private final UUID propertyId;

    public PropertyNotFoundException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.propertyId = null;
    }

    public PropertyNotFoundException(UUID propertyId) {
        super("Property not found: " + propertyId);
        this.errorCode = ErrorCode.PROPERTY_NOT_FOUND;
        this.propertyId = propertyId;
    }

    public PropertyNotFoundException(UUID propertyId, String message) {
        super(message);
        this.errorCode = ErrorCode.PROPERTY_NOT_FOUND;
        this.propertyId = propertyId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public UUID getPropertyId() {
        return propertyId;
    }
}