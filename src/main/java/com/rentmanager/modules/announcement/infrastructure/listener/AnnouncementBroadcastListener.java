package com.rentmanager.modules.announcement.infrastructure.listener;

import com.rentmanager.modules.announcement.application.AnnouncementDispatchTrigger;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.announcement.domain.events.AnnouncementCreated;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.domain.service.AnnouncementFanoutPlanner;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fans an AnnouncementCreated event out into one AnnouncementDelivery row
 * per active renter per selected channel - exactly the set the send
 * preview counted. Runs AFTER_COMMIT in its own transaction: whatever
 * happens downstream (a full provider outage, a broken profile), the
 * Announcement write itself is already committed and is never rolled
 * back.
 *
 * WhatsApp rows for renters without captured opt-in are created as
 * SKIPPED_NO_OPTIN (never attempted, never retried); SMS/email/in-app
 * still reach those renters. IN_APP rows are DELIVERED at creation. The
 * actual external sends happen on the single announcement dispatch thread
 * - kicked off immediately by {@link AnnouncementDispatchTrigger}, with
 * the scheduled sweep as the retry safety net - so the HTTP request never
 * iterates recipients synchronously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementBroadcastListener {

    private final AnnouncementQueryService announcementQueryService;
    private final AnnouncementDeliveryRepository deliveryRepository;
    private final AnnouncementDispatchTrigger dispatchTrigger;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAnnouncementCreated(AnnouncementCreated event) {
        try {
            UUID tenantId = event.getTenantId();

            List<TenantProfile> activeProfiles = announcementQueryService.activeProfiles(tenantId);
            List<AnnouncementFanoutPlanner.Target> targets =
                    AnnouncementFanoutPlanner.plan(event.getChannels(), activeProfiles);

            List<AnnouncementDelivery> rows = new ArrayList<>(targets.size());
            for (AnnouncementFanoutPlanner.Target target : targets) {
                rows.add(createRow(event, target));
            }

            deliveryRepository.saveAll(rows);

            log.info("Announcement {} fan-out enqueued: {} delivery row(s) for {} active renter(s), channels={}",
                    event.getAnnouncementId(), rows.size(), activeProfiles.size(), event.getChannels());

            dispatchTrigger.dispatchNow();
        } catch (Exception ex) {
            log.error("Failed to fan out announcement: {}", event.getAnnouncementId(), ex);
        }
    }

    private AnnouncementDelivery createRow(AnnouncementCreated event, AnnouncementFanoutPlanner.Target target) {
        return switch (target.initialStatus()) {
            case DELIVERED -> AnnouncementDelivery.createDelivered(
                    event.getTenantId(), event.getAnnouncementId(), target.renterProfileId());
            case SKIPPED_NO_OPTIN -> AnnouncementDelivery.createSkippedNoOptIn(
                    event.getTenantId(), event.getAnnouncementId(), target.renterProfileId());
            default -> AnnouncementDelivery.create(
                    event.getTenantId(), event.getAnnouncementId(), target.renterProfileId(), target.channel());
        };
    }
}
