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

        // FIX: was setField(e, "tenantId", lease.getTenantId()) via raw
        // reflection, which bypassed BaseTenantEntity's tenant-isolation
        // guard entirely and could silently move a row to a different
        // tenant on an update path. assignTenantIfUnset() assigns on first
        // save (tenantId null) and is a safe no-op on update when the
        // tenantId matches, but now throws IllegalStateException if it
        // doesn't match instead of silently reassigning.
        e.assignTenantIfUnset(lease.getTenantId());

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

        // Lifecycle metadata — real getters on Lease.java (this session).
        e.setSignedAt(lease.getSignedAt());
        e.setActivatedAt(lease.getActivatedAt());
        e.setTerminatedAt(lease.getTerminatedAt());
        e.setExpiredAt(lease.getExpiredAt());
        e.setRenewedAt(lease.getRenewedAt());
        e.setCancelledAt(lease.getCancelledAt());
        e.setTerminationType(lease.getTerminationType());
        e.setTerminationReason(lease.getTerminationReason());

        // FIX (Track B): previously missing entirely — no column, no
        // setter call. Every lease saved before this fix silently
        // discarded lateFeeAmount/gracePeriodDays/autoRenew on write.
        // Requires LeaseEntity to have these three columns (migration +
        // entity fields added alongside this change).
        e.setLateFeeAmount(lease.getLateFeeAmount());
        e.setGracePeriodDays(lease.getGracePeriodDays());
        e.setAutoRenew(lease.isAutoRenew());
    }

    /**
     * ENTITY → DOMAIN (SAFE RECONSTRUCTION)
     *
     * NOTE: still uses reflection here, unlike updateEntity() above. Lease
     * has no public setters for these fields (by design — they're only
     * ever supposed to change via its own lifecycle methods like
     * terminate()/cancel()/expire()/renew()), so there's no equivalent
     * "real setter" to switch to on this side. This asymmetry is
     * intentional: reflection stays confined to reconstructing a Lease
     * from storage, never to normal domain mutation.
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

        // timestamps restore (test stability + audit correctness)
        setField(lease, "createdAt", e.getCreatedAt());
        setField(lease, "updatedAt", e.getUpdatedAt());

        // lifecycle metadata restore — previously always null after any
        // save/reload cycle regardless of what the domain object had set.
        setField(lease, "signedAt", e.getSignedAt());
        setField(lease, "activatedAt", e.getActivatedAt());
        setField(lease, "terminatedAt", e.getTerminatedAt());
        setField(lease, "expiredAt", e.getExpiredAt());
        setField(lease, "renewedAt", e.getRenewedAt());
        setField(lease, "cancelledAt", e.getCancelledAt());
        setField(lease, "terminationType", e.getTerminationType());
        setField(lease, "terminationReason", e.getTerminationReason());

        // FIX (Track B): previously missing entirely — restore() never
        // took these params and nothing reflected them back in either,
        // so every reload came back with lateFeeAmount=null,
        // gracePeriodDays=null, autoRenew=false regardless of what was
        // actually in the database (once updateEntity() above is also
        // fixed to actually persist them).
        setField(lease, "lateFeeAmount", e.getLateFeeAmount());
        setField(lease, "gracePeriodDays", e.getGracePeriodDays());
        setField(lease, "autoRenew", e.isAutoRenew());

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