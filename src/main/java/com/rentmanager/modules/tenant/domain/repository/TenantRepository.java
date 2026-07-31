package com.rentmanager.modules.tenant.domain.repository;



import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.Tenant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

 // ------------------------------------------------
 // FINDERS
 // ------------------------------------------------

 Optional<Tenant> findById(UUID id);

 Optional<Tenant> findBySlug(String slug);

 Optional<Tenant> findByTenantCode(String tenantCode);

 List<Tenant> findAll();


 Optional<Tenant> findByClerkOrgId(String clerkOrgId);
 // ------------------------------------------------
 // VALIDATION QUERIES
 // ------------------------------------------------

 boolean existsBySlug(String slug);

 boolean existsByTenantCode(String tenantCode);

 // ------------------------------------------------
 // PREMIUM BILLING SWEEPS (PHASE 1 DUAL REVENUE MODEL)
 // ------------------------------------------------

 /** Active premium tenants whose paid period has ended and auto-renew is on. */
 List<Tenant> findPremiumRenewalsDue(LocalDate today);

 /** Active premium tenants whose paid period has ended and auto-renew is off. */
 List<Tenant> findPremiumNonRenewalsDue(LocalDate today);

 /** Premium tenants in the grace window whose grace period has ended unpaid. */
 List<Tenant> findPremiumGraceOverdue(LocalDate today);

 // ------------------------------------------------
 // SEARCH
 // ------------------------------------------------

 List<Tenant> findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(
         String name,
         String tenantCode
 );

 // ------------------------------------------------
 // PERSISTENCE
 // ------------------------------------------------

 Tenant save(Tenant tenant);
}