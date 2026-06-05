package com.rentmanager.modules.auth.domain.model;

import com.rentmanager.modules.auth.domain.enums.AuthProvider;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AuthUser {

    private final UUID id;
    private final UUID tenantId;

    private String email;
    private String password;

    private AuthProvider provider;

    private boolean active;

    private final Set<Role> roles = new HashSet<>();

    private final Instant createdAt;
    private Instant updatedAt;

    public AuthUser(
            UUID id,
            UUID tenantId,
            String email,
            String password,
            AuthProvider provider
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.password = password;
        this.provider = provider;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void assignRole(Role role) {
        this.roles.add(role);
        touch();
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
        touch();
    }

    public void deactivate() {
        this.active = false;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public AuthProvider getProvider() { return provider; }
    public boolean isActive() { return active; }
    public Set<Role> getRoles() { return roles; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}