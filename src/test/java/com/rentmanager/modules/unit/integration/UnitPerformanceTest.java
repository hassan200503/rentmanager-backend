/*package com.rentmanager.modules.unit.integration;

import com.rentmanager.RentManagerApplication;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.factory.UnitTestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                RentManagerApplication.class,
                PostgresSpringBridge.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@Transactional
class UnitPerformanceTest {

    @Autowired
    private UnitCommandService service;

    @Test
    void shouldHandleBulkUnitCreation() {

        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID propertyId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        int count = 500;

        List<UnitResponse> responses = new ArrayList<>(count);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {

            UnitResponse response = service.create(
                    tenantId,
                    UnitTestDataFactory.createUnitRequest(propertyId)
            );

            responses.add(response);
        }

        long duration = System.currentTimeMillis() - startTime;

        // ---------------- VALIDATION ----------------

        assertEquals(count, responses.size(), "Not all units were processed");

        for (UnitResponse response : responses) {

            assertNotNull(response, "Response is null");
            assertNotNull(response.getId(), "Unit ID is null");

            assertEquals(tenantId, response.getTenantId(), "Tenant isolation broken");
            assertNotNull(response.getUnitNumber(), "Unit number missing");
        }

        // ---------------- PERFORMANCE ----------------

        assertTrue(duration < 15000,
                () -> "Bulk unit creation too slow: " + duration + "ms");
    }
}

 */