/*package com.rentmanager.modules.tenant.integration;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.factory.TenantDbCleaner;
import com.rentmanager.modules.tenant.factory.TenantTestDataFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                RentManagerApplication.class,
                PostgresSpringBridge.class,
                TenantSaaSIntegrationTest.TestConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
class TenantSaaSIntegrationTest {

    @Autowired
    private TenantTestDataFactory factory;

    @Autowired
    private TenantRepository repository;

    @Autowired
    private EntityManager em;

    @Autowired
    private TenantDbCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TenantTestDataFactory tenantTestDataFactory(TenantRepository repository) {
            return new TenantTestDataFactory(repository);
        }

        @Bean
        public TenantDbCleaner tenantDbCleaner() {
            return new TenantDbCleaner();
        }
    }

    private Tenant persist(Tenant tenant) {
        Tenant saved = repository.save(tenant);
        em.flush();
        em.clear();
        return repository.findById(saved.getId()).orElseThrow();
    }

    @Test
    void should_create_tenant_in_pending_state() {

        Tenant tenant = factory.createTenant(
                "Alpha Ltd",
                "alpha",
                "alpha@company.com",
                TenantType.STANDARD
        );

        Tenant persisted = repository.findById(tenant.getId()).orElseThrow();

        assertEquals(TenantStatus.PENDING, persisted.getStatus());
        assertTrue(persisted.isActive());
    }

    @Test
    void should_activate_tenant_correctly() {

        Tenant tenant = factory.createTenant(
                "Beta Ltd",
                "beta",
                "beta@company.com",
                TenantType.STANDARD
        );

        tenant.activate();
        tenant = persist(tenant);

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
    }

    @Test
    void should_suspend_active_tenant() {

        Tenant tenant = factory.createTenant(
                "Gamma Ltd",
                "gamma",
                "gamma@company.com",
                TenantType.STANDARD
        );

        tenant.activate();
        tenant.suspend();
        tenant = persist(tenant);

        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus());
    }

    @Test
    void should_prevent_deactivated_tenant_reactivation() {

        Tenant tenant = factory.createTenant(
                "Delta Ltd",
                "delta",
                "delta@company.com",
                TenantType.STANDARD
        );

        tenant.deactivate();
        tenant = persist(tenant);

        assertThrows(IllegalStateException.class, tenant::activate);
    }

    @Test
    void should_update_subscription_status_independently() {

        Tenant tenant = factory.createTenant(
                "Omega Ltd",
                "omega",
                "omega@company.com",
                TenantType.STANDARD
        );

        tenant.updateSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenant = persist(tenant);

        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
    }

    @Test
    void should_update_branding_settings() {

        Tenant tenant = factory.createTenant(
                "Brand Ltd",
                "brand",
                "brand@company.com",
                TenantType.STANDARD
        );

        tenant.updateBranding(BrandingSettings.of(
                "logo.png",
                "favicon.ico",
                "#FFFFFF",
                "#000000"
        ));

        tenant = persist(tenant);

        assertEquals("#FFFFFF", tenant.getBrandingSettings().getPrimaryColor());
    }
}


 */
