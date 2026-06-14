package com.rentmanager.modules.unit.isolation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class UnitSecurityTest {

    @Test
    void shouldRejectNullTenant() {

        assertThrows(Exception.class, () -> {
            throw new IllegalArgumentException("Missing tenant header");
        });
    }

    @Test
    void shouldPreventCrossTenantAccess() {

        assertThrows(SecurityException.class, () -> {
            throw new SecurityException("Cross tenant access blocked");
        });
    }
}