package com.rentmanager.modules.tenant.isolation;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                RentManagerApplication.class,
                PostgresSpringBridge.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Transactional
class TenantIsolationPenetrationTest  extends AbstractPostgresIntegrationTest {

    @Autowired
    private TenantRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void should_persist_and_retrieve_tenants_without_identity_loss() {

        String s1 = UUID.randomUUID().toString().substring(0, 8);
        String s2 = UUID.randomUUID().toString().substring(0, 8);

        Tenant t1 = repository.save(Tenant.create(
                "T-" + s1,
                "Tenant A",
                "slug-" + s1,
                s1 + "@mail.com",
                "0700",
                TenantType.STANDARD
        ));

        Tenant t2 = repository.save(Tenant.create(
                "T-" + s2,
                "Tenant B",
                "slug-" + s2,
                s2 + "@mail.com",
                "0700",
                TenantType.STANDARD
        ));

        entityManager.flush();
        entityManager.clear();

        assertNotNull(t1.getId());
        assertNotNull(t2.getId());

        assertNotNull(t1.getCreatedAt(), "createdAt must be auto-generated");
        assertNotNull(t2.getCreatedAt(), "createdAt must be auto-generated");

        Tenant r1 = repository.findById(t1.getId())
                .orElseThrow(() -> new AssertionError("Tenant 1 missing"));

        Tenant r2 = repository.findById(t2.getId())
                .orElseThrow(() -> new AssertionError("Tenant 2 missing"));

        assertEquals(t1.getId(), r1.getId());
        assertEquals(t2.getId(), r2.getId());

        assertNotEquals(r1.getId(), r2.getId());

        assertEquals(t1.getEmail(), r1.getEmail());
        assertEquals(t2.getEmail(), r2.getEmail());

        assertNotNull(r1.getCreatedAt());
        assertNotNull(r2.getCreatedAt());

        assertTrue(r1.getCreatedAt().isBefore(Instant.now().plusSeconds(5)));
    }
}



