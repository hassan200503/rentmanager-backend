package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentReminderPolicyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RentReminderPolicyJpaRepository
        extends JpaRepository<RentReminderPolicyJpaEntity, UUID> {

    List<RentReminderPolicyJpaEntity> findByTenantId(UUID tenantId);
}
