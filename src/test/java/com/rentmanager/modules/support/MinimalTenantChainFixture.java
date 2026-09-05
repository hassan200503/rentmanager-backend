package com.rentmanager.modules.support;

import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * properties/units/leases/tenant_profile/rent_ledger_entries all carry real
 * foreign keys to tenants (and to each other) now — see migrations
 * V72-V75. Plenty of integration tests fabricate a random tenantId (and
 * property/unit/tenant_profile ids) purely to satisfy a domain factory's
 * required parameters, with no interest in the parent rows' actual content.
 * This inserts the minimal placeholder chain those tests need via raw SQL,
 * bypassing the domain model entirely so tests stay focused on whatever
 * they're actually exercising.
 */
public final class MinimalTenantChainFixture {

    private MinimalTenantChainFixture() {}

    public record Chain(UUID tenantId, UUID propertyId, UUID unitId, UUID tenantProfileId) {}

    public record ChainWithLease(UUID tenantId, UUID propertyId, UUID unitId, UUID tenantProfileId, UUID leaseId) {}

    public static UUID persistTenant(EntityManager entityManager) {
        UUID tenantId = UUID.randomUUID();
        entityManager.createNativeQuery("INSERT INTO tenants (id, tenant_code, name) VALUES (?1, ?2, 'Test Landlord')")
                .setParameter(1, tenantId).setParameter(2, "TEN-" + tenantId).executeUpdate();
        return tenantId;
    }

    /**
     * Idempotently ensures a tenant/property pair exists at fixed, caller-
     * chosen ids — for tests (several in the unit module) that share the
     * same literal UUID constants across multiple independent test classes
     * running against the same reused Testcontainers instance, where a
     * second plain INSERT would collide on the primary key.
     */
    public static void ensureTenantAndProperty(EntityManager entityManager, UUID tenantId, UUID propertyId) {
        entityManager.createNativeQuery("""
                INSERT INTO tenants (id, tenant_code, name) VALUES (?1, ?2, 'Test Landlord')
                ON CONFLICT (id) DO NOTHING
                """)
                .setParameter(1, tenantId).setParameter(2, "TEN-" + tenantId).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO properties (id, tenant_id, reference_code, name, status, created_at, premises_type)
                VALUES (?1, ?2, ?3, 'Test Property', 'ACTIVE', NOW(), 'RESIDENTIAL')
                ON CONFLICT (id) DO NOTHING
                """)
                .setParameter(1, propertyId).setParameter(2, tenantId).setParameter(3, "PROP-" + propertyId)
                .executeUpdate();
    }

    public static UUID persistProperty(EntityManager entityManager, UUID tenantId) {
        UUID propertyId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO properties (id, tenant_id, reference_code, name, status, created_at, premises_type)
                VALUES (?1, ?2, ?3, 'Test Property', 'ACTIVE', NOW(), 'RESIDENTIAL')
                """)
                .setParameter(1, propertyId).setParameter(2, tenantId).setParameter(3, "PROP-" + propertyId)
                .executeUpdate();
        return propertyId;
    }

    public static UUID persistUnit(EntityManager entityManager, UUID tenantId, UUID propertyId) {
        UUID unitId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO units (id, unit_number, tenant_id, property_id, status, occupancy_status)
                VALUES (?1, ?2, ?3, ?4, 'ACTIVE', 'VACANT')
                """)
                .setParameter(1, unitId).setParameter(2, "U-" + unitId)
                .setParameter(3, tenantId).setParameter(4, propertyId)
                .executeUpdate();
        return unitId;
    }

    public static UUID persistTenantProfile(EntityManager entityManager, UUID tenantId) {
        UUID tenantProfileId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO tenant_profile (id, tenant_id, clerk_user_id, full_name, email, phone)
                VALUES (?1, ?2, ?3, 'Test Renter', 'renter@test.local', '+254711111111')
                """)
                .setParameter(1, tenantProfileId).setParameter(2, tenantId).setParameter(3, "clerk-" + tenantProfileId)
                .executeUpdate();
        return tenantProfileId;
    }

    /** Full tenant + property + unit + tenant_profile chain, one call. */
    public static Chain persistFullChain(EntityManager entityManager) {
        UUID tenantId = persistTenant(entityManager);
        UUID propertyId = persistProperty(entityManager, tenantId);
        UUID unitId = persistUnit(entityManager, tenantId, propertyId);
        UUID tenantProfileId = persistTenantProfile(entityManager, tenantId);
        entityManager.flush();
        return new Chain(tenantId, propertyId, unitId, tenantProfileId);
    }

    public static UUID persistLease(
            EntityManager entityManager, UUID tenantId, UUID propertyId, UUID unitId, UUID tenantProfileId
    ) {
        UUID leaseId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO leases
                    (id, tenant_id, property_id, unit_id, tenant_profile_id, lease_number,
                     lease_type, billing_cycle, status, start_date, end_date, rent_amount, deposit_amount)
                VALUES
                    (?1, ?2, ?3, ?4, ?5, ?6, 'FIXED_TERM', 'MONTHLY', 'ACTIVE',
                     '2026-01-01', '2026-12-31', 1000.00, 1000.00)
                """)
                .setParameter(1, leaseId).setParameter(2, tenantId).setParameter(3, propertyId)
                .setParameter(4, unitId).setParameter(5, tenantProfileId).setParameter(6, "LSE-" + leaseId)
                .executeUpdate();
        return leaseId;
    }

    /** Full tenant + property + unit + tenant_profile + lease chain, one call. */
    public static ChainWithLease persistFullChainWithLease(EntityManager entityManager) {
        Chain chain = persistFullChain(entityManager);
        UUID leaseId = persistLease(entityManager, chain.tenantId(), chain.propertyId(), chain.unitId(), chain.tenantProfileId());
        entityManager.flush();
        return new ChainWithLease(chain.tenantId(), chain.propertyId(), chain.unitId(), chain.tenantProfileId(), leaseId);
    }
}
