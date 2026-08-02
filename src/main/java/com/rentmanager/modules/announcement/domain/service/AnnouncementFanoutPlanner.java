package com.rentmanager.modules.announcement.domain.service;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The single source of truth for "who gets this announcement on which
 * channel". Both the send preview (recipient + channel counts shown to
 * the landlord BEFORE confirm) and the broadcast fan-out (delivery rows
 * created AFTER confirm) consume this planner - so the preview count the
 * landlord confirms always matches what the broadcast job actually
 * executes.
 *
 * Rules per active renter per selected channel:
 * <ul>
 *   <li>IN_APP - always delivered (the channel is always selected).</li>
 *   <li>SMS - delivered when the profile has a phone.</li>
 *   <li>EMAIL - delivered when the profile has an email.</li>
 *   <li>WHATSAPP - delivered ONLY with captured opt-in; otherwise the
 *       target is created as SKIPPED_NO_OPTIN (never attempted).</li>
 * </ul>
 */
public final class AnnouncementFanoutPlanner {

    public record Target(UUID renterProfileId, AnnouncementChannel channel, AnnouncementDeliveryStatus initialStatus) {
        public boolean isSkipped() {
            return initialStatus == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN;
        }
    }

    private AnnouncementFanoutPlanner() {
    }

    public static List<Target> plan(Set<AnnouncementChannel> channels, List<TenantProfile> activeProfiles) {
        Set<AnnouncementChannel> selected = channels == null ? Set.of() : channels;

        List<Target> targets = new ArrayList<>();
        for (TenantProfile profile : activeProfiles) {
            if (selected.contains(AnnouncementChannel.IN_APP)) {
                targets.add(new Target(profile.getId(), AnnouncementChannel.IN_APP, AnnouncementDeliveryStatus.DELIVERED));
            }
            if (selected.contains(AnnouncementChannel.SMS) && profile.getPhone() != null && !profile.getPhone().isBlank()) {
                targets.add(new Target(profile.getId(), AnnouncementChannel.SMS, AnnouncementDeliveryStatus.PENDING));
            }
            if (selected.contains(AnnouncementChannel.EMAIL) && profile.getEmail() != null && !profile.getEmail().isBlank()) {
                targets.add(new Target(profile.getId(), AnnouncementChannel.EMAIL, AnnouncementDeliveryStatus.PENDING));
            }
            if (selected.contains(AnnouncementChannel.WHATSAPP)) {
                AnnouncementDeliveryStatus status = profile.isWhatsAppOptIn()
                        ? AnnouncementDeliveryStatus.PENDING
                        : AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN;
                targets.add(new Target(profile.getId(), AnnouncementChannel.WHATSAPP, status));
            }
        }
        return targets;
    }

    /**
     * Distinct channels actually targeted (used by the delivery row
     * creation and the preview to keep the channel set in lockstep).
     */
    public static Set<AnnouncementChannel> targetedChannels(List<Target> targets) {
        Set<AnnouncementChannel> channels = new LinkedHashSet<>();
        for (Target target : targets) {
            channels.add(target.channel());
        }
        return channels;
    }
}
