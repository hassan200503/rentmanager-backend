package com.rentmanager.modules.maintenance.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceRequestNotificationListener {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenanceRequestSubmitted(MaintenanceRequestSubmitted event) {
        try {
            UUID tenantId = event.getTenantId();

            TenantProfile renterProfile = tenantProfileRepository.findById(event.getTenantProfileId())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found: " + event.getTenantProfileId()));

            Unit unit = unitRepository.findByIdAndTenantId(event.getUnitId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + event.getUnitId()));

            Tenant landlord = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Landlord not found: " + tenantId));

            String requestId = "MNT-" + event.getRequestId().toString().substring(0, 8).toUpperCase();

            if (renterProfile.getPhone() != null && !renterProfile.getPhone().isBlank()) {
                smsService.sendMaintenanceRequestConfirmation(
                        renterProfile.getPhone(),
                        event.getTitle(),
                        requestId
                );
            }

            if (landlord.getPhoneNumber() != null && !landlord.getPhoneNumber().isBlank()) {
                smsService.sendMaintenanceRequestNotificationToLandlord(
                        landlord.getPhoneNumber(),
                        renterProfile.getFullName(),
                        unit.getUnitNumber(),
                        event.getTitle()
                );
            }
        } catch (Exception ex) {
            log.error("Failed to send maintenance request notifications for event: {}", event.getEventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenanceRequestStatusChanged(MaintenanceRequestStatusChanged event) {
        try {
            TenantProfile renterProfile = tenantProfileRepository.findById(event.getTenantProfileId())
                    .orElse(null);
            if (renterProfile == null || renterProfile.getPhone() == null || renterProfile.getPhone().isBlank()) return;

            smsService.sendMaintenanceRequestStatusUpdate(
                    renterProfile.getPhone(),
                    "Maintenance Request",
                    event.getNewStatus().name()
            );
        } catch (Exception ex) {
            log.error("Failed to send maintenance status update for event: {}", event.getEventId(), ex);
        }
    }
}
