package com.rentmanager.modules.tenant.lifecycle;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantLifecycleTest {

    private Tenant create() {
        return Tenant.create(
                "TNT-100",
                "Lifecycle Ltd",
                "lifecycle-ltd",
                "admin@life.com",
                "+254700000000",
                TenantType.STANDARD
        );
    }

    @Test
    void should_activate_tenant() {

        Tenant tenant = create();

        tenant.activate();

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertTrue(tenant.isActive());
    }

    @Test
    void should_suspend_tenant() {

        Tenant tenant = create();

        tenant.suspend();

        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus());
        assertFalse(tenant.isActive());
    }

    @Test
    void should_deactivate_tenant() {

        Tenant tenant = create();

        tenant.deactivate();

        assertEquals(TenantStatus.DEACTIVATED, tenant.getStatus());
        assertFalse(tenant.isActive());
    }

    @Test
    void should_not_reactivate_deactivated_tenant() {

        Tenant tenant = create();
        tenant.deactivate();

        IllegalStateException ex = assertThrows(IllegalStateException.class, tenant::activate);

        assertEquals("Deactivated tenant cannot be reactivated", ex.getMessage());
    }
}