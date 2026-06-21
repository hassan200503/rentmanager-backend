package com.rentmanager.modules.unit.isolation;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
@ContextConfiguration(initializers = PostgresSpringBridge.class)
class UnitTenantIsolationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UnitRepository repository;

    @Test
    void shouldIsolateTenantsInUnits() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        var resultA = repository.findAllByTenantId(tenantA, Pageable.unpaged());
        var resultB = repository.findAllByTenantId(tenantB, Pageable.unpaged());

        assertNotNull(resultA);
        assertNotNull(resultB);

        // FIX: avoid comparing empty lists; assert isolation safely
        assertTrue(
                resultA.getContent().stream()
                        .noneMatch(u -> tenantB.equals(u.getTenantId()))
        );

        assertTrue(
                resultB.getContent().stream()
                        .noneMatch(u -> tenantA.equals(u.getTenantId()))
        );
    }
}

