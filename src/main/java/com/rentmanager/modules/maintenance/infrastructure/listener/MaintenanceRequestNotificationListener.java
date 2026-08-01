package com.rentmanager.modules.maintenance.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
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

/**
 * Phase 5: fans maintenance-request events out into the notification
 * outbox. Each channel is an independent delivery row, so one channel
 * failing can never block the others or the request write itself. Actual
 * sends happen asynchronously via NotificationRetryScheduler, which
 * retries failures with backoff and never re-sends a SENT row.
 *
 * SMS/email/WhatsApp body text deliberately mirrors the messages the
 * previous direct-SMS implementation sent - content is unchanged, only
 * the delivery mechanism is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaintenanceRequestNotificationListener {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final NotificationDeliveryRepository notificationDeliveryRepository;

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
                enqueue(tenantId, event.getEventId(), NotificationChannel.SMS,
                        renterProfile.getPhone(), null,
                        """
                        Maintenance request received: "%s".
                        Ref: %s. We will notify you when there is an update.
                        - RentManager""".formatted(event.getTitle(), requestId));
            }

            if (landlord.getPhoneNumber() != null && !landlord.getPhoneNumber().isBlank()) {
                String landlordMessage = """
                        Maintenance request from %s for unit %s: "%s".
                        Log in to your dashboard to review and assign.
                        - RentManager""".formatted(renterProfile.getFullName(), unit.getUnitNumber(), event.getTitle());

                enqueue(tenantId, event.getEventId(), NotificationChannel.SMS,
                        landlord.getPhoneNumber(), null, landlordMessage);
                enqueue(tenantId, event.getEventId(), NotificationChannel.WHATSAPP,
                        landlord.getPhoneNumber(), null, landlordMessage);
            }

            if (landlord.getEmail() != null && !landlord.getEmail().isBlank()) {
                enqueue(tenantId, event.getEventId(), NotificationChannel.EMAIL,
                        landlord.getEmail(), "New maintenance request: " + event.getTitle(),
                        """
                        %s reported "%s" for unit %s.
                        Log in to your dashboard to review and assign.
                        - RentManager""".formatted(renterProfile.getFullName(), event.getTitle(), unit.getUnitNumber()));
            }
        } catch (Exception ex) {
            log.error("Failed to enqueue maintenance request notifications for event: {}", event.getEventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenanceRequestStatusChanged(MaintenanceRequestStatusChanged event) {
        try {
            TenantProfile renterProfile = tenantProfileRepository.findById(event.getTenantProfileId())
                    .orElse(null);
            if (renterProfile == null || renterProfile.getPhone() == null || renterProfile.getPhone().isBlank()) {
                return;
            }

            String statusLabel = event.getNewStatus().name().toLowerCase().replace('_', ' ');

            enqueue(event.getTenantId(), event.getEventId(), NotificationChannel.SMS,
                    renterProfile.getPhone(), null,
                    """
                    Update on "Maintenance Request": %s.
                    Log in to your RentManager portal for details.
                    - RentManager""".formatted(statusLabel));
        } catch (Exception ex) {
            log.error("Failed to enqueue maintenance status update for event: {}", event.getEventId(), ex);
        }
    }

    private void enqueue(
            UUID tenantId,
            UUID eventId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String message
    ) {
        try {
            NotificationDelivery delivery = NotificationDelivery.create(
                    tenantId, eventId, channel, recipient, subject, message, null);
            notificationDeliveryRepository.save(delivery);
        } catch (Exception ex) {
            log.error("Failed to enqueue {} notification to {}: {}",
                    channel, recipient, ex.getMessage());
        }
    }
}
