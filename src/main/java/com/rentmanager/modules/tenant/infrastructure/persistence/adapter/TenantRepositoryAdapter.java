package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;


import com.rentmanager.modules.tenant.domain.repository.TenantRepository;

import com.rentmanager.modules.tenant.infrastructure.persistence.mapper.TenantPersistenceMapper;

import com.rentmanager.modules.tenant.infrastructure.persistence.repository.TenantJpaRepository;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;
    private final TenantPersistenceMapper mapper;

    public TenantRepositoryAdapter(
            TenantJpaRepository jpaRepository,
            TenantPersistenceMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    // ------------------------------------------------
// PERSISTENCE
// ------------------------------------------------
    @Override
    public Tenant save(Tenant tenant) {

        TenantEntity entity = mapper.toJpaEntity(tenant);

        TenantEntity saved = jpaRepository.save(entity);

        // IMPORTANT: ensure DB constraints + ID generation happen

        return mapper.toDomain(saved);
    }
    // ------------------------------------------------
    // FINDERS
    // ------------------------------------------------
    @Override
    public Optional<Tenant> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }

        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findBySlug(String slug) {
        return jpaRepository.findBySlug(slug)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Tenant> findByTenantCode(String tenantCode) {
        return jpaRepository.findByTenantCode(tenantCode)
                .map(mapper::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    // ------------------------------------------------
    // VALIDATION QUERIES
    // ------------------------------------------------

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public boolean existsByTenantCode(String tenantCode) {
        return jpaRepository.existsByTenantCode(tenantCode);
    }

    // ------------------------------------------------
    // SEARCH
    // ------------------------------------------------

    @Override
    public List<Tenant> findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(
            String name,
            String tenantCode
    ) {
        return jpaRepository
                .findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(name, tenantCode)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }



    @Override
    public Optional<Tenant> findByClerkOrgId(String clerkOrgId) {
        return jpaRepository.findByClerkOrgId(clerkOrgId)
                .map(mapper::toDomain);
    }

    // ------------------------------------------------
    // PREMIUM BILLING SWEEPS (PHASE 1 DUAL REVENUE MODEL)
    // ------------------------------------------------
    @Override
    public List<Tenant> findPremiumRenewalsDue(LocalDate today) {
        return jpaRepository
                .findByBillingModeAndSubscriptionStatusAndPlanAutoRenewTrueAndPlanEndDateLessThanEqual(
                        BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, today
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Tenant> findPremiumNonRenewalsDue(LocalDate today) {
        return jpaRepository
                .findByBillingModeAndSubscriptionStatusAndPlanAutoRenewFalseAndPlanEndDateLessThanEqual(
                        BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.ACTIVE, today
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Tenant> findPremiumGraceOverdue(LocalDate today) {
        return jpaRepository
                .findByBillingModeAndSubscriptionStatusAndPlanGraceEndsAtLessThanEqual(
                        BillingMode.PREMIUM_MONTHLY, SubscriptionStatus.GRACE_PERIOD, today
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}