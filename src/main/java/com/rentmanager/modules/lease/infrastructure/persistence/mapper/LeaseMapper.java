package com.rentmanager.modules.lease.infrastructure.persistence.mapper;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class LeaseMapper {

    /**
     * DOMAIN → ENTITY
     */
    public void updateEntity(LeaseEntity e, Lease lease) {


        if (lease.getId() != null) {
            setField(e, "id", lease.getId());
        }
        setField(e, "tenantId", lease.getTenantId());

        e.setPropertyId(lease.getPropertyId());
        e.setUnitId(lease.getUnitId());
        e.setTenantProfileId(lease.getTenantProfileId());

        e.setLeaseNumber(lease.getLeaseNumber());
        e.setLeaseType(lease.getLeaseType());
        e.setBillingCycle(lease.getBillingCycle());

        e.setStartDate(lease.getStartDate());
        e.setEndDate(lease.getEndDate());

        e.setRentAmount(lease.getRentAmount());
        e.setDepositAmount(lease.getSecurityDeposit());

        e.setStatus(lease.getStatus());
    }
    /**
     * ENTITY → DOMAIN (SAFE RECONSTRUCTION)
     */
    public Lease toDomain(LeaseEntity e) {

        Lease lease = Lease.restore(
                e.getId(),
                e.getTenantId(),
                e.getPropertyId(),
                e.getUnitId(),
                e.getTenantProfileId(),
                e.getLeaseNumber(),
                e.getLeaseType(),
                e.getBillingCycle(),
                e.getStartDate(),
                e.getEndDate(),
                e.getRentAmount(),
                e.getDepositAmount(),
                e.getStatus()
        );


        // identity restore
        setField(lease, "id", e.getId());
        setField(lease, "tenantId", e.getTenantId());

        // lifecycle restore
        setField(lease, "status", e.getStatus());

        // timestamps RESTORE (IMPORTANT FIX FOR TEST STABILITY)
        setField(lease, "createdAt", e.getCreatedAt());
        setField(lease, "updatedAt", e.getUpdatedAt());
        return lease;
    }


    public LeaseEntity toEntity(Lease lease) {

        LeaseEntity entity = new LeaseEntity();

// ensure ID consistency
        if (lease.getId() != null) {
            setField(entity, "id", lease.getId());
        }

        updateEntity(entity, lease);

        return entity;
    }
    /**
     * Reflection-based safe field restoration (no domain leaks)
     */
    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map field: " + fieldName, ex);
        }
    }
}
