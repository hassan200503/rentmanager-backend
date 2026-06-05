package com.rentmanager.modules.auth.domain.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Role {

    private final UUID id;
    private final String name;

    private final Set<Permission> permissions = new HashSet<>();

    public Role(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Set<Permission> getPermissions() { return permissions; }
}