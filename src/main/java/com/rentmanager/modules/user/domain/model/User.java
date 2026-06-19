package com.rentmanager.modules.user.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private String clerkUserId;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private boolean active;

    private User(String clerkUserId, String email) {
        this.clerkUserId = clerkUserId;
        this.email = email;
        this.active = true;
    }

    public static User createFromClerk(String clerkUserId, String email) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("Clerk user ID is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return new User(clerkUserId, email);
    }

    public static User rehydrate(
            UUID id,
            Long version,
            String clerkUserId,
            UUID tenantId,
            String email,
            String firstName,
            String lastName,
            boolean active
    ) {
        User user = new User();
        user.setId(id);
        user.setVersion(version);
        user.clerkUserId = clerkUserId;
        user.tenantId = tenantId;
        user.email = email;
        user.firstName = firstName;
        user.lastName = lastName;
        user.active = active;
        return user;
    }

    public void assignTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        this.tenantId = tenantId;
    }
}