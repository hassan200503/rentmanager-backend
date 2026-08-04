package com.rentmanager.modules.property.integration;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PropertyCommandService service;

    @Autowired
    private PropertyRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userId;

    @BeforeEach
    void setup() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userId = UUID.randomUUID();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Order(1)
    void shouldCreatePropertySuccessfully() {

        var request = baseRequest("Green Villa");

        var response = service.createProperty(tenantA, userId, request);

        assertNotNull(response.getPropertyId());
        assertEquals("Green Villa", response.getName());

        entityManager.flush();
        entityManager.clear();

        var saved = repository.findByIdAndTenantId(
                response.getPropertyId(),
                tenantA
        );

        assertTrue(saved.isPresent());
        assertEquals(tenantA, saved.get().getTenantId());
    }

    @Test
    @Order(2)
    void shouldEnforceTenantIsolation() {

        var response = service.createProperty(
                tenantA,
                userId,
                baseRequest("Isolation Property")
        );

        entityManager.flush();
        entityManager.clear();

        assertTrue(
                repository.findByIdAndTenantId(response.getPropertyId(), tenantB).isEmpty(),
                "Tenant isolation violated"
        );
    }

    @Test
    @Order(3)
    void shouldUpdatePropertySuccessfully() {

        var created = service.createProperty(
                tenantA,
                userId,
                baseRequest("Old Name")
        );

        var update = new UpdatePropertyRequest();
        update.setName("New Name");
        update.setDescription("Updated desc");

        var updated = service.updateProperty(
                tenantA,
                created.getPropertyId(),
                update
        );

        assertEquals("New Name", updated.getName());

        entityManager.flush();
        entityManager.clear();

        var fromDb = repository.findByIdAndTenantId(
                created.getPropertyId(),
                tenantA
        );

        assertTrue(fromDb.isPresent());
        assertEquals("New Name", fromDb.get().getName());
    }

    @Test
    @Order(4)
    void shouldHandleFullLifecycle() {

        var created = service.createProperty(
                tenantA,
                userId,
                baseRequest("Lifecycle Property")
        );

        service.activateProperty(tenantA, created.getPropertyId());
        service.archiveProperty(tenantA, created.getPropertyId());

        entityManager.flush();
        entityManager.clear();

        var finalState = repository.findByIdAndTenantId(
                created.getPropertyId(),
                tenantA
        );

        assertTrue(finalState.isPresent());
        assertEquals("Lifecycle Property", finalState.get().getName());
    }

    private CreatePropertyRequest baseRequest(String name) {
        var request = new CreatePropertyRequest();
        request.setName(name);
        request.setPropertyType(PropertyType.APARTMENT);
        request.setDescription("Test property");
        return request;
    }
}

