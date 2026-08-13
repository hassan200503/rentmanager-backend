package com.rentmanager.modules.integration.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.IntegrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "platform_integration_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_integration_provider_env",
                columnNames = {"provider_key", "environment"}
        )
)
public class IntegrationConfigEntity extends BaseEntity {

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private IntegrationEnvironment environment;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "text")
    private String encryptedCredentials;

    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IntegrationStatus status = IntegrationStatus.NOT_CONFIGURED;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_verified_by", length = 200)
    private String lastVerifiedBy;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;
}