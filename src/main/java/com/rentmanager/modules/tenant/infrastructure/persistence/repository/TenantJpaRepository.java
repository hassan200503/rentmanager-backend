package com.rentmanager.modules.tenant.infrastructure.persistence.repository;


import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    Optional<TenantEntity> findByTenantCode(String tenantCode);
    Optional<TenantEntity> findByClerkOrgId(String clerkOrgId);

    List<TenantEntity> findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(
            String name,
            String code
    );

    boolean existsBySlug(String slug);

    // �o. ADDED �?" required by domain + adapter
    boolean existsByTenantCode(String tenantCode);

    // ---------------------------------------------------------------
    // PREMIUM BILLING SWEEPS (PHASE 1) - used by SubscriptionBillingScheduler
    // ---------------------------------------------------------------

    /** Active premium tenants whose paid period has ended and auto-renew is on. */
    List<TenantEntity> findByBillingModeAndSubscriptionStatusAndPlanAutoRenewTrueAndPlanEndDateLessThanEqual(
            BillingMode billingMode, SubscriptionStatus subscriptionStatus, LocalDate today
    );

    /** Active premium tenants whose paid period has ended and auto-renew is off (voluntary downgrade). */
    List<TenantEntity> findByBillingModeAndSubscriptionStatusAndPlanAutoRenewFalseAndPlanEndDateLessThanEqual(
            BillingMode billingMode, SubscriptionStatus subscriptionStatus, LocalDate today
    );

    /** Premium tenants in the grace window whose grace period has ended unpaid. */
    List<TenantEntity> findByBillingModeAndSubscriptionStatusAndPlanGraceEndsAtLessThanEqual(
            BillingMode billingMode, SubscriptionStatus subscriptionStatus, LocalDate today
    );
}