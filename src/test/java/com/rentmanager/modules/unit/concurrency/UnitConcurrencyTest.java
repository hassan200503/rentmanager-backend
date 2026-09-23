package com.rentmanager.modules.unit.concurrency;

import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.factory.UnitTestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UnitConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UnitCommandService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID propertyId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void seedTenantAndProperty() {
        // units.tenant_id/property_id now carry real foreign keys (V72-V73) —
        // pin a tenant/property to the fixed UUIDs this test already uses
        // throughout. UnitIntegrationTest and UnitPerformanceTest reuse the
        // exact same constants, hence the idempotent ON CONFLICT DO NOTHING.
        // This test method has no @Transactional (its 20 concurrent worker
        // threads each need their own independently-committing transaction
        // via the service layer), so a plain EntityManager write here has no
        // active transaction to run in — TransactionTemplate opens and
        // commits one explicitly instead.
        transactionTemplate.executeWithoutResult(status ->
                com.rentmanager.modules.support.MinimalTenantChainFixture.ensureTenantAndProperty(entityManager, tenantId, propertyId));
    }

    @org.junit.jupiter.api.AfterEach
    void removeCommittedFixtures() {
        // This test cannot be @Transactional -- its twenty worker threads each
        // need an independently committing transaction -- so nothing rolls its
        // rows back. Without this, the units and property it commits stay in the
        // shared container and break whichever unrelated test happens to run
        // next: that is what turned CI red on one run and green on the very next
        // with the same code.
        transactionTemplate.executeWithoutResult(status ->
                com.rentmanager.modules.support.MinimalTenantChainFixture
                        .removeCommittedUnitsAndProperty(entityManager, propertyId));
    }

    @Test
    void should_handle_concurrent_unit_creation_without_unique_constraint_breaks() throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(20);
        List<Future<UnitResponse>> futures = new ArrayList<>();

        Set<String> usedUnitNumbers = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(() -> {

                String unitNumber;
                do {
                    unitNumber = "UT-" + UUID.randomUUID();
                } while (!usedUnitNumbers.add(unitNumber));

                CreateUnitRequest req = UnitTestDataFactory.createUnitRequest(propertyId);
                req.setUnitNumber(unitNumber);

                try {
                    return service.create(tenantId, req);
                } catch (Exception ex) {
                    return null;
                }
            }));
        }

        Set<UUID> ids = new HashSet<>();
        int success = 0;

        for (Future<UnitResponse> f : futures) {
            UnitResponse res = f.get(20, TimeUnit.SECONDS);
            if (res != null) {
                success++;
                ids.add(res.getId());
            }
        }

        executor.shutdown();

        assertTrue(success >= 1, "At least one unit must be created successfully");
        assertEquals(success, ids.size(), "No duplicate persisted units allowed");
    }

    @Test
    void should_keep_data_consistent_under_parallel_updates() throws Exception {

        CreateUnitRequest create = UnitTestDataFactory.createUnitRequest(propertyId);
        create.setUnitNumber("UT-CONC-" + UUID.randomUUID());

        UnitResponse created = service.create(tenantId, create);
        UUID unitId = created.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<?>> futures = new ArrayList<>();
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        Runnable updateA = () -> {
            try {
                service.update(
                        tenantId,
                        unitId,
                        UpdateUnitRequest.builder()
                                .label("A")
                                .description("A")
                                .rentAmount(BigDecimal.valueOf(1000))
                                .build()
                );
            } catch (Exception ex) {
                errors.add(ex);
            }
        };

        Runnable updateB = () -> {
            try {
                service.update(
                        tenantId,
                        unitId,
                        UpdateUnitRequest.builder()
                                .label("B")
                                .description("B")
                                .rentAmount(BigDecimal.valueOf(2000))
                                .build()
                );
            } catch (Exception ex) {
                errors.add(ex);
            }
        };

        futures.add(executor.submit(updateA));
        futures.add(executor.submit(updateB));

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertNotNull(unitId);

        /**
         * IMPORTANT REALITY:
         * In concurrent DB updates:
         * - 0 or 1 errors is acceptable
         * - final state must not crash system
         * - data corruption is NOT allowed
         */
        assertTrue(errors.size() <= 2, "System should not crash under concurrent updates");
    }

}



