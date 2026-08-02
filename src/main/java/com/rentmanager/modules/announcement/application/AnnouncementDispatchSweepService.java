package com.rentmanager.modules.announcement.application;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.modules.notification.application.NotificationDispatchService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches due announcement deliveries. Mirrors the Phase 5
 * NotificationDispatchSweepService shape: one pass pulls a bounded batch
 * of due rows and each row is handled independently - a failure on one
 * recipient/channel can never block the rest of the broadcast, and the
 * dispatch result is recorded per row. SENT/DELIVERED/SKIPPED_NO_OPTIN
 * rows are never re-dispatched; FAILED rows retry with backoff and give
 * up permanently after {@link AnnouncementDelivery#MAX_ATTEMPTS} failures.
 *
 * Message shaping per channel (the constraint that shapes the whole
 * feature): SMS, email and in-app carry the landlord's FULL message text;
 * WhatsApp carries the approved Utility-template version with the message
 * truncated to the template's limit - it drives the renter back into the
 * app instead of carrying free text Meta would reject.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementDispatchSweepService {

    private final AnnouncementDeliveryRepository deliveryRepository;
    private final AnnouncementRepository announcementRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final TenantRepository tenantRepository;
    private final NotificationDispatchService dispatchService;
    private final AnnouncementWhatsAppTemplate whatsAppTemplate;
    private final AnnouncementProperties properties;

    /**
     * Dispatches up to batch-size due rows. Returns how many were
     * dispatched so callers (the drain loop) know when the queue is
     * empty. Runs on the single dispatch thread - never concurrent with
     * itself.
     */
    @Transactional
    public int dispatchDue() {
        List<AnnouncementDelivery> due = deliveryRepository.findDue(Instant.now(), properties.getBatchSize());
        if (due.isEmpty()) {
            return 0;
        }
        Map<UUID, String> landlordNames = new HashMap<>();
        for (AnnouncementDelivery delivery : due) {
            dispatchOne(delivery, landlordNames);
        }
        log.info("Announcement sweep dispatched {} due delivery(ies)", due.size());
        return due.size();
    }

    /**
     * Dispatches a single delivery. The transaction boundary is the whole
     * pass (mirroring the notification sweep): exceptions are caught per
     * row, so one broken recipient can never roll back the SENT statuses
     * of the others.
     */
    void dispatchOne(AnnouncementDelivery delivery, Map<UUID, String> landlordNames) {
        try {
            if (delivery.getChannel() == AnnouncementChannel.IN_APP) {
                return;
            }

            Announcement announcement = announcementRepository
                    .findByIdAndTenantId(delivery.getAnnouncementId(), delivery.getTenantId())
                    .orElse(null);
            if (announcement == null) {
                delivery.recordFailure("Announcement not found");
                deliveryRepository.save(delivery);
                return;
            }

            TenantProfile profile = tenantProfileRepository.findById(delivery.getRenterProfileId()).orElse(null);
            if (profile == null) {
                delivery.recordFailure("Renter profile not found");
                deliveryRepository.save(delivery);
                return;
            }

            String landlordName = landlordNames.computeIfAbsent(delivery.getTenantId(), this::loadLandlordName);
            boolean accepted = dispatch(delivery, announcement, profile, landlordName);

            if (accepted) {
                delivery.markSent();
            } else {
                delivery.recordFailure("Provider did not confirm delivery");
            }
        } catch (Exception e) {
            log.error("Announcement delivery {} ({}) failed: {}",
                    delivery.getId(), delivery.getChannel(), e.getMessage());
            delivery.recordFailure(String.valueOf(e.getMessage()));
        }
        deliveryRepository.save(delivery);
    }

    private boolean dispatch(
            AnnouncementDelivery delivery,
            Announcement announcement,
            TenantProfile profile,
            String landlordName
    ) {
        return switch (delivery.getChannel()) {
            case SMS -> dispatchService.dispatch(
                    AnnouncementChannel.SMS.toNotificationChannel(),
                    profile.getPhone(),
                    null,
                    smsBody(announcement.getMessage()),
                    null);
            case EMAIL -> dispatchService.dispatch(
                    AnnouncementChannel.EMAIL.toNotificationChannel(),
                    profile.getEmail(),
                    "New announcement from " + landlordName,
                    smsBody(announcement.getMessage()),
                    null);
            case WHATSAPP -> dispatchService.dispatch(
                    AnnouncementChannel.WHATSAPP.toNotificationChannel(),
                    profile.getPhone(),
                    null,
                    whatsAppTemplate.format(landlordName, announcement.getMessage()),
                    whatsAppTemplate.templateName());
            case IN_APP -> true;
        };
    }

    /**
     * SMS/email/in-app carry the landlord's full, untruncated message -
     * only WhatsApp is truncated to the template limit.
     */
    private String smsBody(String message) {
        return message + "\n- RentManager";
    }

    private String loadLandlordName(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElse(null);
    }
}
