package com.rentmanager.shared.exception;

import java.util.List;

public class ValidationException extends BaseException {

    private final List<FieldError> errors;

    public ValidationException(String message, List<FieldError> errors) {
        super(message, ErrorCode.VALIDATION_ERROR);
        this.errors = errors;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}