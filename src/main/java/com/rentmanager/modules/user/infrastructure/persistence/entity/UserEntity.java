package com.rentmanager.modules.user.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.user.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_clerk_user_id", columnList = "clerk_user_id"),
                @Index(name = "idx_users_tenant_id", columnList = "tenant_id")
        }
)
@NoArgsConstructor
public class UserEntity extends BaseEntity {

    @Setter
    @Column(name = "clerk_user_id", nullable = false, unique = true, length = 255)
    private String clerkUserId;

    @Setter
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Setter
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Setter
    @Column(name = "first_name", length = 150)
    private String firstName;

    @Setter
    @Column(name = "last_name", length = 150)
    private String lastName;

    @Setter
    @Column(name = "active", nullable = false)
    private boolean active;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private UserRole role;
}