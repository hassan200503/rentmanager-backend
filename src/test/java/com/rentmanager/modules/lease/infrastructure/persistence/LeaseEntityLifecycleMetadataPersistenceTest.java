package com.rentmanager.modules.lease.infrastructure.persistence;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.lease.infrastructure.persistence.repository.JpaLeaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the LeaseEntity/LeaseMapper fix from this session: all seven
 * lifecycle metadata fields (signedAt, activatedAt, terminatedAt, expiredAt,
 * renewedAt, cancelledAt, terminationType, terminationReason) must survive
 * a save -> reload cycle against a real Postgres instance (matching V34's
 * TIMESTAMPTZ columns).
 *
 * Style matched against TenantSaaSIntegrationTest. One deliberate deviation:
 * that test uses a TenantTestDataFactory/TenantDbCleaner pair; no equivalent
 * Lease factory/cleaner was confirmed to exist, so this test builds
 * LeaseEntity directly and relies on @Transactional's automatic rollback
 * per test instead of an explicit cleaner. If a LeaseTestDataFactory or
 * LeaseDbCleaner does exist, tell me and I'll switch to match exactly.
 *
 * NOTE: tenantId is assigned via BaseTenantEntity.assignTenant(UUID), not a
 * setter -- BaseTenantEntity deliberately has no public setTenantId(), by
 * design, to enforce tenant-isolation safety (assign-once guard).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@ActiveProfiles("test")
@Transactional
class LeaseEntityLifecycleMetadataPersistenceTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JpaLeaseRepository leaseRepository;

    @Autowired
    private EntityManager em;

    private LeaseEntity persist(LeaseEntity entity) {
        LeaseEntity saved = leaseRepository.save(entity);
        em.flush();
        em.clear();
        return leaseRepository.findById(saved.getId()).orElseThrow();
    }

    @Test
    void allLifecycleMetadataFieldsSurviveSaveAndReload() {
        LocalDateTime signedAt = LocalDateTime.now().minusDays(30);
        LocalDateTime activatedAt = LocalDateTime.now().minusDays(29);
        LocalDateTime terminatedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime renewedAt = LocalDateTime.now().minusDays(10);
        TerminationType terminationType = TerminationType.TENANT_REQUEST;
        String terminationReason = "Tenant relocating for work";

        LeaseEntity entity = new LeaseEntity();
        entity.assignTenant(UUID.randomUUID());
        entity.setPropertyId(UUID.randomUUID());
        entity.setUnitId(UUID.randomUUID());
        entity.setTenantProfileId(UUID.randomUUID());
        entity.setLeaseNumber("LSE-TEST-" + UUID.randomUUID());
        entity.setLeaseType(LeaseType.FIXED_TERM);
        entity.setBillingCycle(BillingCycle.MONTHLY);
        entity.setStartDate(LocalDate.now().minusMonths(1));
        entity.setEndDate(LocalDate.now().plusMonths(11));
        entity.setRentAmount(new BigDecimal("1500.00"));
        entity.setDepositAmount(new BigDecimal("1500.00"));
        entity.setStatus(LeaseStatus.TERMINATED);

        entity.setSignedAt(signedAt);
        entity.setActivatedAt(activatedAt);
        entity.setTerminatedAt(terminatedAt);
        entity.setExpiredAt(null);
        entity.setRenewedAt(renewedAt);
        entity.setCancelledAt(null);
        entity.setTerminationType(terminationType);
        entity.setTerminationReason(terminationReason);

        LeaseEntity reloaded = persist(entity);

        assertEquals(signedAt.withNano(0), reloaded.getSignedAt().withNano(0));
        assertEquals(activatedAt.withNano(0), reloaded.getActivatedAt().withNano(0));
        assertEquals(terminatedAt.withNano(0), reloaded.getTerminatedAt().withNano(0));
        assertNull(reloaded.getExpiredAt());
        assertEquals(renewedAt.withNano(0), reloaded.getRenewedAt().withNano(0));
        assertNull(reloaded.getCancelledAt());
        assertEquals(terminationType, reloaded.getTerminationType());
        assertEquals(terminationReason, reloaded.getTerminationReason());
    }
}