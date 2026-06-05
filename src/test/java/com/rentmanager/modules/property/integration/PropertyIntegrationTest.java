package com.rentmanager.modules.property.integration;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = RentManagerApplication.class)
@Transactional
class PropertyIntegrationTest {

    @Autowired
    private PropertyCommandService service;

    @Autowired
    private PropertyRepository repository;

    @Autowired
    private EntityManager entityManager;

    private final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldCreatePropertySuccessfully() {

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Green Villa");
        request.setPropertyType(PropertyType.APARTMENT);
        request.setDescription("Luxury unit");

        var response = service.createProperty(TENANT_A, request);

        assertNotNull(response.getPropertyId());
        assertEquals("Green Villa", response.getName());

        entityManager.flush();
        entityManager.clear();

        var saved = repository.findByIdAndTenantId(
                response.getPropertyId(),
                TENANT_A
        );

        assertTrue(saved.isPresent());
        assertEquals("Green Villa", saved.get().getName());
    }

    @Test
    void shouldEnforceTenantIsolation() {

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Tenant A Property");
        request.setPropertyType(PropertyType.BEDSITTER);

        var response = service.createProperty(TENANT_A, request);

        entityManager.flush();
        entityManager.clear();

        assertTrue(
                repository.findByIdAndTenantId(
                        response.getPropertyId(),
                        TENANT_B
                ).isEmpty()
        );
    }

    @Test
    void shouldUpdatePropertySuccessfully() {

        CreatePropertyRequest create = new CreatePropertyRequest();
        create.setName("Old Name");
        create.setPropertyType(PropertyType.APARTMENT);

        var created = service.createProperty(TENANT_A, create);

        UpdatePropertyRequest update = new UpdatePropertyRequest();
        update.setName("New Name");
        update.setDescription("Updated desc");

        var updated = service.updateProperty(
                TENANT_A,
                created.getPropertyId(),
                update
        );

        assertEquals("New Name", updated.getName());

        entityManager.flush();
        entityManager.clear();

        var fromDb = repository.findByIdAndTenantId(
                created.getPropertyId(),
                TENANT_A
        );

        assertTrue(fromDb.isPresent());
        assertEquals("New Name", fromDb.get().getName());
    }

    @Test
    void shouldHandleFullLifecycle() {

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Lifecycle Property");
        request.setPropertyType(PropertyType.VILLA);

        var created = service.createProperty(TENANT_A, request);

        var activated = service.activateProperty(
                TENANT_A,
                created.getPropertyId()
        );

        assertEquals("Lifecycle Property", activated.getName());

        var archived = service.archiveProperty(
                TENANT_A,
                created.getPropertyId()
        );

        assertEquals("Lifecycle Property", archived.getName());

        entityManager.flush();
        entityManager.clear();

        var finalState = repository.findByIdAndTenantId(
                created.getPropertyId(),
                TENANT_A
        );

        assertTrue(finalState.isPresent());
        assertNotNull(finalState.get().getStatus());
    }
}
