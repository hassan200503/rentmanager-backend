package com.rentmanager.modules.tenant.domain;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantDomainTest {

    @Test
    void should_create_tenant_with_default_state() {

        Tenant tenant = Tenant.create(
                "TNT-001",
                "Acme Ltd",
                "acme-ltd",
                "admin@acme.com",
                "+254700000000",
                TenantType.STANDARD
        );

        assertNotNull(tenant);

        // Default lifecycle state (from constructor)
        assertEquals(TenantStatus.PENDING, tenant.getStatus());
        assertEquals(SubscriptionStatus.TRIAL, tenant.getSubscriptionStatus());

        // IMPORTANT:
        // isActive() depends on status == ACTIVE
        // NEW tenants are PENDING → NOT active
        assertFalse(tenant.isActive());
    }

    @Test
    void should_create_tenant_with_custom_subscription_status() {

        Tenant tenant = Tenant.create(
                "TNT-002",
                "Beta Ltd",
                "beta-ltd",
                "admin@beta.com",
                "+254711111111",
                TenantType.STANDARD,
                SubscriptionStatus.ACTIVE
        );

        assertEquals(SubscriptionStatus.ACTIVE, tenant.getSubscriptionStatus());
    }

    @Test
    void should_fail_when_tenant_code_is_blank() {

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Tenant.create(
                        "",
                        "Name",
                        "slug",
                        "email@x.com",
                        "+254",
                        TenantType.STANDARD
                )
        );

        assertEquals("Tenant code is required", ex.getMessage());
    }

    @Test
    void should_fail_when_slug_is_blank() {

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Tenant.create(
                        "TNT-003",
                        "Name",
                        "",
                        "email@x.com",
                        "+254",
                        TenantType.STANDARD
                )
        );

        assertEquals("Tenant slug is required", ex.getMessage());
    }

    @Test
    void should_fail_when_email_is_blank() {

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Tenant.create(
                        "TNT-004",
                        "Name",
                        "slug",
                        "",
                        "+254",
                        TenantType.STANDARD
                )
        );

        assertEquals("Tenant email is required", ex.getMessage());
    }
}