package com.rentmanager.modules.unit.lifecycle;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UnitTest {

    @Test
    void should_create_unit_in_initial_state() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-001",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        assertNotNull(unit.getId());
        assertEquals(UnitStatus.INACTIVE, unit.getStatus());
        assertEquals(UnitOccupancyStatus.VACANT, unit.getOccupancyStatus());
    }

    @Test
    void should_activate_unit() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-002",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.activate("corr-2");

        assertEquals(UnitStatus.ACTIVE, unit.getStatus());
    }

    @Test
    void should_deactivate_unit() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-003",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.activate("corr-2");
        unit.deactivate("corr-3");

        assertEquals(UnitStatus.INACTIVE, unit.getStatus());
    }

    @Test
    void should_mark_unit_occupied() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-004",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.markOccupied("corr-2");

        assertEquals(UnitOccupancyStatus.OCCUPIED, unit.getOccupancyStatus());
    }

    @Test
    void should_mark_unit_vacant() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-005",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.markOccupied("corr-2");
        unit.markVacant("corr-3");

        assertEquals(UnitOccupancyStatus.VACANT, unit.getOccupancyStatus());


    }


    @Test
    void should_be_idempotent_when_activating_twice() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-006",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.activate("corr-2");
        unit.activate("corr-3"); // second call

        assertEquals(UnitStatus.ACTIVE, unit.getStatus());
    }

    @Test
    void should_not_change_state_when_marking_same_occupancy() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-007",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.markOccupied("corr-2");
        unit.markOccupied("corr-3"); // no change expected

        assertEquals(UnitOccupancyStatus.OCCUPIED, unit.getOccupancyStatus());
    }


    @Test
    void should_archive_unit() {

        Unit unit = Unit.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "U-008",
                "Test Unit",
                BigDecimal.valueOf(1000),
                "desc",
                "corr-1"
        );

        unit.archive("corr-2");

        assertEquals(UnitStatus.ARCHIVED, unit.getStatus());
    }
}