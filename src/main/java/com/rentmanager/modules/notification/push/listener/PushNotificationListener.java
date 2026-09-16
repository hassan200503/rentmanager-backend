package com.rentmanager.modules.notification.push.listener;

import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.notification.push.application.PushNotificationService;
import com.rentmanager.modules.notification.push.domain.PushCategory;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns committed domain events into push deliveries.
 *
 * AFTER_COMMIT + REQUIRES_NEW, like every other notification listener: a push
 * can never roll back the write that caused it, and can never announce a
 * payment or status change that did not commit.
 *
 * <h3>Isolation</h3>
 * Recipients are resolved only from the event's own landlord organisation
 * ({@code tenantId}). A renter profile loaded by id is checked to belong to
 * that same organisation before anything is queued.
 *
 * <h3>Lock-screen copy</h3>
 * Deliberately generic — see {@link PushNotificationService}. The {@code type}
 * values are the mobile app's deep-link contract; change them in both places.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationListener {

    static final String TYPE_RENT_PAYMENT = "rent_payment_recorded";
    static final String TYPE_RENTER_MAINTENANCE = "renter_maintenance_updated";
    static final String TYPE_LANDLORD_MAINTENANCE = "landlord_maintenance_submitted";

    private static final int MAX_ORG_RECIPIENTS = 50;

    private final PushNotificationService pushNotificationService;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRentPaymentApplied(RentPaymentApplied event) {
        try {
            leaseRepository.findByIdAndTenantId(event.getLeaseId(), event.getTenantId())
                    .flatMap(lease -> renterInOrganisation(lease.getTenantProfileId(), event.getTenantId()))
                    .ifPresent(profile -> pushNotificationService.enqueueForPerson(
                            event.getTenantId(), event.getEventId(), profile.getClerkUserId(),
                            PushCategory.RENT_PAYMENTS,
                            "Payment recorded",
                            "A payment was recorded on your rent account. Tap to view it.",
                            Map.of("type", TYPE_RENT_PAYMENT,
                                    "id", String.valueOf(event.getTransactionId()))));
        } catch (Exception ex) {
            log.error("Failed to queue payment push for event {}", event.getEventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenanceStatusChanged(MaintenanceRequestStatusChanged event) {
        try {
            renterInOrganisation(event.getTenantProfileId(), event.getTenantId())
                    .ifPresent(profile -> pushNotificationService.enqueueForPerson(
                            event.getTenantId(), event.getEventId(), profile.getClerkUserId(),
                            PushCategory.MAINTENANCE,
                            "Maintenance update",
                            "There is an update on a request you reported. Tap to see it.",
                            Map.of("type", TYPE_RENTER_MAINTENANCE,
                                    "id", String.valueOf(event.getRequestId()))));
        } catch (Exception ex) {
            log.error("Failed to queue maintenance status push for event {}", event.getEventId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaintenanceSubmitted(MaintenanceRequestSubmitted event) {
        try {
            userRepository.findByTenantId(event.getTenantId(), PageRequest.of(0, MAX_ORG_RECIPIENTS))
                    .getContent().stream()
                    .filter(User::isActive)
                    .filter(user -> Objects.equals(user.getTenantId(), event.getTenantId()))
                    .forEach(user -> pushNotificationService.enqueueForPerson(
                            event.getTenantId(), event.getEventId(), user.getClerkUserId(),
                            PushCategory.MAINTENANCE,
                            "New maintenance request",
                            "A renter reported an issue. Tap to review it.",
                            Map.of("type", TYPE_LANDLORD_MAINTENANCE,
                                    "id", String.valueOf(event.getRequestId()))));
        } catch (Exception ex) {
            log.error("Failed to queue landlord maintenance push for event {}", event.getEventId(), ex);
        }
    }

    private Optional<TenantProfile> renterInOrganisation(UUID tenantProfileId, UUID tenantId) {
        if (tenantProfileId == null) {
            return Optional.empty();
        }
        return tenantProfileRepository.findById(tenantProfileId)
                .filter(profile -> Objects.equals(profile.getTenantId(), tenantId))
                .filter(profile -> profile.getClerkUserId() != null && !profile.getClerkUserId().isBlank());
    }
}
