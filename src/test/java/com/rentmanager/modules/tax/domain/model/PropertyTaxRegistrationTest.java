package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.modules.tax.domain.enums.PropertyTaxRegistrationStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PropertyTaxRegistrationTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();

    @Test
    void initiate_setsPendingAndDenormalisesPins() {
        PropertyTaxRegistration registration = PropertyTaxRegistration.initiate(
                TENANT_ID, PROPERTY_ID, "P000000000A", "P111111111K");

        assertEquals(PropertyTaxRegistrationStatus.PENDING, registration.getStatus());
        assertEquals(TENANT_ID, registration.getTenantId());
        assertEquals("P000000000A", registration.getLandlordKraPin());
        assertEquals("P111111111K", registration.getTenantKraPin());
        assertNotNull(registration.getId());
    }

    @Test
    void initiate_rejectsMissingTenantOrProperty() {
        assertThrows(IllegalArgumentException.class,
                () -> PropertyTaxRegistration.initiate(null, PROPERTY_ID, "A", null));
        assertThrows(IllegalArgumentException.class,
                () -> PropertyTaxRegistration.initiate(TENANT_ID, null, "A", null));
    }

    @Test
    void lifecycle_transmitAccept() {
        PropertyTaxRegistration registration = PropertyTaxRegistration.initiate(
                TENANT_ID, PROPERTY_ID, "P000000000A", null);

        registration.markTransmitted();
        assertEquals(PropertyTaxRegistrationStatus.TRANSMITTED, registration.getStatus());

        registration.markAccepted("KRA-REG-42");
        assertEquals(PropertyTaxRegistrationStatus.ACCEPTED, registration.getStatus());
        assertEquals("KRA-REG-42", registration.getKrPropertyRegistrationId());
        assertNotNull(registration.getRegisteredAt());
    }

    @Test
    void markRejected_recordsError() {
        PropertyTaxRegistration registration = PropertyTaxRegistration.initiate(
                TENANT_ID, PROPERTY_ID, "P000000000A", null);

        registration.markRejected("pin mismatch");

        assertEquals(PropertyTaxRegistrationStatus.REJECTED, registration.getStatus());
        assertEquals("pin mismatch", registration.getLastError());
    }

    @Test
    void markReadyForManual_handsOffForManualFiling() {
        PropertyTaxRegistration registration = PropertyTaxRegistration.initiate(
                TENANT_ID, PROPERTY_ID, null, null);

        registration.markReadyForManual();

        assertEquals(PropertyTaxRegistrationStatus.READY_FOR_MANUAL, registration.getStatus());
    }
}