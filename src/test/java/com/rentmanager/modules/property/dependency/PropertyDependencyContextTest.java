package com.rentmanager.modules.property.dependency;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Shared Testcontainers Postgres: a bare @SpringBootTest reached localhost:5432,
// which exists on a developer machine and not in CI.
class PropertyDependencyContextTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PropertyCommandService service;

    @Test
    void shouldLoadPropertyCommandServiceBean() {
        assertNotNull(service);
    }
}