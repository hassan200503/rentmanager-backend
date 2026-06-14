/*package com.rentmanager.modules.unit.integration;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.crossmodule.support.PostgresTestContainerConfig;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.factory.UnitDbCleaner;
import com.rentmanager.modules.unit.factory.UnitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                RentManagerApplication.class,
                UnitIntegrationTest.TestConfig.class,
                com.rentmanager.crossmodule.support.PostgresSpringBridge.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
class UnitIntegrationTest {

    @Autowired
    private UnitCommandService service;

    @Autowired
    private UnitDbCleaner cleaner;

    @TestConfiguration
    static class TestConfig {

        @Bean
        public UnitDbCleaner unitDbCleaner(EntityManager em) {
            return new UnitDbCleaner(em);
        }
    }

    @Test
    void shouldRunFullUnitLifecycle() {

        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID propertyId = UUID.fromString("22222222-2222-2222-2222-222222222222");

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

 */