package com.rentmanager.modules.unit.integration;

import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.factory.UnitTestDataFactory;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnitIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UnitCommandService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** Same fixed ids the lifecycle test seeds below. */
    private static final UUID FIXTURE_PROPERTY_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @org.junit.jupiter.api.AfterEach
    void removeCommittedFixtures() {
        // This test method is deliberately not @Transactional, so its rows are
        // committed to the shared container and outlive it. Cleaning up in
        // @AfterEach rather than at the end of the test means it still happens
        // when an assertion fails partway through -- which is exactly when a
        // leak would otherwise be left behind and confuse the next class.
        transactionTemplate.executeWithoutResult(status ->
                MinimalTenantChainFixture.removeCommittedUnitsAndProperty(entityManager, FIXTURE_PROPERTY_ID));
    }

    @Test
    void shouldRunFullUnitLifecycle() {

        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID propertyId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        // No @Transactional on this test method, so the fixture insert needs
        // its own explicit transaction — see UnitConcurrencyTest for the
        // same pattern and why.
        transactionTemplate.executeWithoutResult(status ->
                MinimalTenantChainFixture.ensureTenantAndProperty(entityManager, tenantId, propertyId));

        CreateUnitRequest request =
                UnitTestDataFactory.createUnitRequest(propertyId);

        var created = service.create(tenantId, request);

        assertNotNull(created);
        assertNotNull(created.getId());

        service.activate(tenantId, created.getId(), "corr-activate");

        service.markOccupied(tenantId, created.getId(), "corr-occupied");

        service.markVacant(tenantId, created.getId(), "corr-vacant");

        service.archive(tenantId, created.getId());

        assertNotNull(created.getId());
    }
}