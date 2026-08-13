package com.rentmanager.modules.integration.infrastructure.persistence.repository;

import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigJpaRepository extends JpaRepository<IntegrationConfigEntity, UUID> {

    Optional<IntegrationConfigEntity> findByProviderKeyAndEnvironment(
            String providerKey, IntegrationEnvironment environment);

    Optional<IntegrationConfigEntity> findByProviderKeyAndActiveTrue(String providerKey);

    List<IntegrationConfigEntity> findByProviderKeyOrderByEnvironmentAsc(String providerKey);

    List<IntegrationConfigEntity> findByActiveTrue();

    @Modifying
    @Query("update IntegrationConfigEntity c set c.active = false " +
            "where c.providerKey = :providerKey and c.environment <> :keepEnvironment")
    void deactivateOthers(@Param("providerKey") String providerKey,
                          @Param("keepEnvironment") IntegrationEnvironment keepEnvironment);
}