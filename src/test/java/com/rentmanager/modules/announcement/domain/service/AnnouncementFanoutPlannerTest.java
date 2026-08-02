package com.rentmanager.modules.announcement.domain.service;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnouncementFanoutPlannerTest {

    @Test
    void plansOneTargetPerRenterPerSelectedChannel() {
        TenantProfile renter = profile("+254712345678", "renter@example.com", false);

        List<AnnouncementFanoutPlanner.Target> targets = AnnouncementFanoutPlanner.plan(
                EnumSet.allOf(AnnouncementChannel.class), List.of(renter));

        assertEquals(4, targets.size());
        assertEquals(AnnouncementDeliveryStatus.DELIVERED, targetFor(targets, AnnouncementChannel.IN_APP).initialStatus());
        assertEquals(AnnouncementDeliveryStatus.PENDING, targetFor(targets, AnnouncementChannel.SMS).initialStatus());
        assertEquals(AnnouncementDeliveryStatus.PENDING, targetFor(targets, AnnouncementChannel.EMAIL).initialStatus());
        assertEquals(AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN, targetFor(targets, AnnouncementChannel.WHATSAPP).initialStatus());
    }

    @Test
    void whatsappIsPendingOnlyWithExplicitOptIn() {
        TenantProfile optedIn = profile("+254712345678", "a@example.com", true);
        TenantProfile notOptedIn = profile("+254712345679", "b@example.com", false);

        List<AnnouncementFanoutPlanner.Target> targets = AnnouncementFanoutPlanner.plan(
                Set.of(AnnouncementChannel.WHATSAPP), List.of(optedIn, notOptedIn));

        assertEquals(2, targets.size());
        assertEquals(AnnouncementDeliveryStatus.PENDING, targetFor(targets, AnnouncementChannel.WHATSAPP).initialStatus());
        assertEquals(1, targets.stream().filter(AnnouncementFanoutPlanner.Target::isSkipped).count(),
                "exactly the non-opted-in renter is skipped");
    }

    @Test
    void unselectedChannelsAreNotPlanned() {
        TenantProfile renter = profile("+254712345678", "a@example.com", true);

        List<AnnouncementFanoutPlanner.Target> targets = AnnouncementFanoutPlanner.plan(
                Set.of(AnnouncementChannel.IN_APP, AnnouncementChannel.EMAIL), List.of(renter));

        assertEquals(2, targets.size());
        assertEquals(Set.of(AnnouncementChannel.IN_APP, AnnouncementChannel.EMAIL),
                AnnouncementFanoutPlanner.targetedChannels(targets));
    }

    @Test
    void smsAndEmailSkippedWhenContactMissing() {
        TenantProfile noContact = profile(null, null, false);

        List<AnnouncementFanoutPlanner.Target> targets = AnnouncementFanoutPlanner.plan(
                EnumSet.allOf(AnnouncementChannel.class), List.of(noContact));

        assertEquals(2, targets.size(), "IN_APP + WHATSAPP(skipped) only");
        assertEquals(AnnouncementChannel.IN_APP, targetFor(targets, AnnouncementChannel.IN_APP).channel());
        assertEquals(AnnouncementChannel.WHATSAPP, targetFor(targets, AnnouncementChannel.WHATSAPP).channel());
        assertTrue(targetFor(targets, AnnouncementChannel.WHATSAPP).isSkipped());
    }

    @Test
    void planIsEmptyForNoRenters() {
        List<AnnouncementFanoutPlanner.Target> targets = AnnouncementFanoutPlanner.plan(
                EnumSet.allOf(AnnouncementChannel.class), List.of());

        assertEquals(0, targets.size());
    }

    private TenantProfile profile(String phone, String email, boolean whatsappOptIn) {
        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(profile.getPhone()).thenReturn(phone);
        when(profile.getEmail()).thenReturn(email);
        when(profile.isWhatsAppOptIn()).thenReturn(whatsappOptIn);
        return profile;
    }

    private AnnouncementFanoutPlanner.Target targetFor(
            List<AnnouncementFanoutPlanner.Target> targets, AnnouncementChannel channel) {
        return targets.stream()
                .filter(t -> t.channel() == channel)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no target for " + channel));
    }
}
