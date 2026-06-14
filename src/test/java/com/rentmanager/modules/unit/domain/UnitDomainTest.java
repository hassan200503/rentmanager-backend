package com.rentmanager.modules.unit.domain;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.event.*;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnitDomainTest {

    private Unit createUnit() {
        return Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-101",
                "Test Unit",
                new BigDecimal("1200"),
                "desc",
                "corr-1"
        );
    }

    // =====================================================
    // CREATION
    // =====================================================

    @Test
    void should_create_unit_with_correct_initial_state() {

        Unit unit = createUnit();

        assertEquals(UnitStatus.INACTIVE, unit.getStatus());
        assertEquals(UnitOccupancyStatus.VACANT, unit.getOccupancyStatus());
        assertEquals("U-101", unit.getUnitNumber());
        assertEquals("Test Unit", unit.getLabel());
        assertEquals(new BigDecimal("1200"), unit.getRentAmount());
    }

    @Test
    void should_register_unit_created_event() {

        Unit unit = createUnit();

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitCreatedEvent));
    }

    // =====================================================
    // UPDATE
    // =====================================================

    @Test
    void should_update_unit_details_and_emit_event() {

        Unit unit = createUnit();

        unit.updateDetails(
                "U-202",
                "Updated Label",
                new BigDecimal("1500"),
                "updated desc",
                "corr-upd"
        );

        assertEquals("U-202", unit.getUnitNumber());
        assertEquals("Updated Label", unit.getLabel());
        assertEquals(new BigDecimal("1500"), unit.getRentAmount());
        assertEquals("updated desc", unit.getDescription());

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitUpdatedEvent));
    }

    // =====================================================
    // STATUS LIFECYCLE
    // =====================================================

    @Test
    void should_activate_unit() {

        Unit unit = createUnit();

        unit.activate("corr-act");

        assertEquals(UnitStatus.ACTIVE, unit.getStatus());

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitActivatedEvent));
    }

    @Test
    void should_deactivate_unit() {

        Unit unit = createUnit();

        unit.activate("corr-act");
        unit.deactivate("corr-deact");

        assertEquals(UnitStatus.INACTIVE, unit.getStatus());

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitDeactivatedEvent));
    }

    @Test
    void should_allow_idempotent_activation() {

        Unit unit = createUnit();

        unit.activate("corr-1");
        unit.activate("corr-2"); // should not break

        assertEquals(UnitStatus.ACTIVE, unit.getStatus());
    }

    @Test
    void should_allow_idempotent_deactivation() {

        Unit unit = createUnit();

        unit.deactivate("corr-1"); // already inactive

        assertEquals(UnitStatus.INACTIVE, unit.getStatus());
    }

    // =====================================================
    // OCCUPANCY
    // =====================================================

    @Test
    void should_mark_unit_occupied() {

        Unit unit = createUnit();

        unit.markOccupied("corr-occ");

        assertEquals(UnitOccupancyStatus.OCCUPIED, unit.getOccupancyStatus());

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitOccupancyChangedEvent));
    }

    @Test
    void should_mark_unit_vacant() {

        Unit unit = createUnit();

        unit.markOccupied("corr-occ");
        unit.markVacant("corr-vac");

        assertEquals(UnitOccupancyStatus.VACANT, unit.getOccupancyStatus());
    }

    @Test
    void should_allow_idempotent_occupancy_changes() {

        Unit unit = createUnit();

        unit.markVacant("corr");

        assertEquals(UnitOccupancyStatus.VACANT, unit.getOccupancyStatus());
    }

    // =====================================================
    // ARCHIVE
    // =====================================================

    @Test
    void should_archive_unit() {

        Unit unit = createUnit();

        unit.archive("corr-arch");

        assertEquals(UnitStatus.ARCHIVED, unit.getStatus());

        List<Object> events = getEvents(unit);

        assertTrue(events.stream().anyMatch(e -> e instanceof UnitArchivedEvent));
    }

    // =====================================================
    // REHYDRATION
    // =====================================================

    @Test
    void should_rehydrate_unit_without_events() {

        Unit unit = Unit.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-1",
                "Label",
                UnitStatus.ACTIVE,
                UnitOccupancyStatus.OCCUPIED,
                new BigDecimal("1000"),
                "desc"
        );

        assertEquals(UnitStatus.ACTIVE, unit.getStatus());
        assertEquals(UnitOccupancyStatus.OCCUPIED, unit.getOccupancyStatus());
    }

    // =====================================================
    // STATUS STRING
    // =====================================================

    @Test
    void should_return_status_as_string() {

        Unit unit = createUnit();

        assertEquals("INACTIVE", unit.getStatusAsString());
    }

    // =====================================================
    // EVENT EXTRACTION HELPER (SAFE FOR UNKNOWN AGGREGATE ROOT)
    // =====================================================

    @SuppressWarnings("unchecked")
    private List<Object> getEvents(Unit unit) {
        try {
            var method = unit.getClass().getMethod("getDomainEvents");
            return (List<Object>) method.invoke(unit);
        } catch (Exception e1) {
            try {
                var method = unit.getClass().getMethod("getEvents");
                return (List<Object>) method.invoke(unit);
            } catch (Exception e2) {
                try {
                    var field = unit.getClass().getSuperclass().getDeclaredField("domainEvents");
                    field.setAccessible(true);
                    return (List<Object>) field.get(unit);
                } catch (Exception ignored) {
                    return List.of(); // fallback safe
                }
            }
        }
    }
}