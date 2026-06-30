package com.rentmanager.modules.tenant.domain.repository;



import com.rentmanager.modules.tenant.domain.model.Tenant;

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