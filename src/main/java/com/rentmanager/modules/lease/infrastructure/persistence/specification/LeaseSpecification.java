package com.rentmanager.modules.lease.infrastructure.persistence.specification;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

/**
 * SaaS-grade dynamic query builder for LeaseEntity.
 *
 * RULES:
 * - NO business logic
 * - ONLY query composition
 * - SAFE for multi-tenant filtering
 */
public class LeaseSpecification {

    /**
     * Filter by tenant (MANDATORY in SaaS systems)
     */
    public static Specification<LeaseEntity> hasTenant(UUID tenantId) {
        return (root, query, cb) ->
                tenantId == null ? null : cb.equal(root.get("tenantId"), tenantId);
    }

    /**
     * Filter by property
     */
    public static Specification<LeaseEntity> hasProperty(UUID propertyId) {
        return (root, query, cb) ->
                propertyId == null ? null : cb.equal(root.get("propertyId"), propertyId);
    }

    /**
     * Filter by unit
     */
    public static Specification<LeaseEntity> hasUnit(UUID unitId) {
        return (root, query, cb) ->
                unitId == null ? null : cb.equal(root.get("unitId"), unitId);
    }

    /**
     * Filter by tenant profile
     */
    public static Specification<LeaseEntity> hasTenantProfile(UUID tenantProfileId) {
        return (root, query, cb) ->
                tenantProfileId == null ? null : cb.equal(root.get("tenantProfileId"), tenantProfileId);
    }

    /**
     * Filter by lease status
     */
    public static Specification<LeaseEntity> hasStatus(String status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    /**
     * Active leases only
     */

    public static Specification<LeaseEntity> isActive() {
        return (root, query, cb) ->
                cb.equal(root.get("status"), LeaseStatus.ACTIVE);
    }

    /**
     * Expired leases (end date before today)
     */
    public static Specification<LeaseEntity> isExpired() {
        return (root, query, cb) ->
                cb.lessThan(root.get("endDate"), LocalDate.now());
    }

    /**
     * Currently valid leases
     */
    public static Specification<LeaseEntity> isCurrentlyValid() {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("startDate"), LocalDate.now()),
                cb.greaterThanOrEqualTo(root.get("endDate"), LocalDate.now())
        );
    }

    /**
     * Date range filtering
     */
    public static Specification<LeaseEntity> startsAfter(LocalDate date) {
        return (root, query, cb) ->
                date == null ? null : cb.greaterThanOrEqualTo(root.get("startDate"), date);
    }

    public static Specification<LeaseEntity> endsBefore(LocalDate date) {
        return (root, query, cb) ->
                date == null ? null : cb.lessThanOrEqualTo(root.get("endDate"), date);
    }

    /**
     * Composite helper (safe SaaS chaining starter)
     */
    public static Specification<LeaseEntity> baseFilter(UUID tenantId) {
        return Specification.where(hasTenant(tenantId));
    }

}