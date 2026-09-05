package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderPolicyRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentReminderPolicyJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentReminderPolicyJpaRepository;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RentReminderPolicyRepositoryAdapter implements RentReminderPolicyRepository {

    private final RentReminderPolicyJpaRepository jpaRepository;

    /**
     * Reuses the existing row's id where one is present so the upsert is an
     * UPDATE rather than an INSERT that would collide with
     * {@code uk_rent_reminder_policies_tenant_milestone}.
     */
    @Override
    @Transactional
    public List<RentReminderPolicy> saveAll(UUID tenantId, List<RentReminderPolicy> policies) {
        Map<ReminderMilestone, RentReminderPolicyJpaEntity> existing =
                jpaRepository.findByTenantId(tenantId).stream()
                        .collect(Collectors.toMap(
                                RentReminderPolicyJpaEntity::getMilestone, e -> e));

        Instant now = Instant.now();
        List<RentReminderPolicyJpaEntity> toSave = new ArrayList<>(policies.size());

        for (RentReminderPolicy policy : policies) {
            RentReminderPolicyJpaEntity entity = existing.get(policy.getMilestone());
            if (entity == null) {
                entity = new RentReminderPolicyJpaEntity();
                entity.setId(UUID.randomUUID());
                entity.setTenantId(tenantId);
                entity.setMilestone(policy.getMilestone());
                entity.setCreatedAt(now);
            }
            entity.setEnabled(policy.isEnabled());
            entity.setSmsEnabled(policy.isSmsEnabled());
            entity.setEmailEnabled(policy.isEmailEnabled());
            entity.setWhatsappEnabled(policy.isWhatsappEnabled());
            entity.setNotifyLandlord(policy.isNotifyLandlord());
            entity.setUpdatedAt(now);
            toSave.add(entity);
        }

        return jpaRepository.saveAll(toSave).stream()
                .map(RentReminderPolicyRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<RentReminderPolicy> findByTenant(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId).stream()
                .map(RentReminderPolicyRepositoryAdapter::toDomain)
                .toList();
    }

    private static RentReminderPolicy toDomain(RentReminderPolicyJpaEntity e) {
        return RentReminderPolicy.rehydrate(
                e.getId(),
                e.getTenantId(),
                e.getMilestone(),
                e.isEnabled(),
                e.isSmsEnabled(),
                e.isEmailEnabled(),
                e.isWhatsappEnabled(),
                e.isNotifyLandlord(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
