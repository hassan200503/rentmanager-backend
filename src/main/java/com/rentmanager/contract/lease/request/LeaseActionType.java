package com.rentmanager.contract.lease.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LeaseActionType {

    APPROVE,
    ACTIVATE,
    TERMINATE,
    REJECT,
    RENEW;

    @JsonCreator
    public static LeaseActionType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("LeaseActionType cannot be null");
        }

        try {
            return LeaseActionType.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid LeaseActionType: " + value +
                            ". Allowed: APPROVE, ACTIVATE, TERMINATE, REJECT, RENEW"
            );
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}