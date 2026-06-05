package com.rentmanager.modules.auth.domain.model;

import java.util.UUID;

public class Permission {

    private final UUID id;
    private final String code;

    public Permission(UUID id, String code) {
        this.id = id;
        this.code = code;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
}