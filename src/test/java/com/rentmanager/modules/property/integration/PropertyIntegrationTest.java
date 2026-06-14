/*package com.rentmanager.modules.property.integration;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(
        classes = RentManagerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PropertyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("rentmanager_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(false); // important for CI stability

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {

        // IMPORTANT: DO NOT CALL start() manually anywhere
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);

        // ensure schema consistency
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private PropertyCommandService service;

    @Autowired
    private PropertyRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantA;
    private UUID tenantB;

    @BeforeEach
    void setup() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Order(1)
    void shouldCreatePropertySuccessfully() {

        var request = baseRequest("Green Villa");

        var response = service.createProperty(tenantA, request);

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

 */