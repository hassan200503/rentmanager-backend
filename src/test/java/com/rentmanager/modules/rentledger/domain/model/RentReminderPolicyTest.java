package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RentReminderPolicyTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void weekAheadReminderIsOffByDefault() {
        RentReminderPolicy policy =
                RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.T_MINUS_7);

        assertThat(policy.isEnabled()).isFalse();
        assertThat(policy.renterChannels()).isEmpty();
        assertThat(policy.landlordChannels()).isEmpty();
    }

    /**
     * SMS is billed to the landlord per message, so the default cadence
     * spends it only where it is most likely to produce a payment.
     */
    @Test
    void smsIsReservedForDueDayAndTheLaterOverdueMilestones() {
        assertThat(RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.T_MINUS_3).isSmsEnabled())
                .isFalse();
        assertThat(RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.OVERDUE_1).isSmsEnabled())
                .isFalse();

        assertThat(RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.DUE_TODAY).isSmsEnabled())
                .isTrue();
        assertThat(RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.OVERDUE_3).isSmsEnabled())
                .isTrue();
        assertThat(RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.OVERDUE_7).isSmsEnabled())
                .isTrue();
    }

    @Test
    void everyEnabledMilestoneSendsEmailBecauseItCostsNothing() {
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            RentReminderPolicy policy = RentReminderPolicy.defaultFor(tenantId, milestone);
            if (policy.isEnabled()) {
                assertThat(policy.renterChannels())
                        .as("%s should include email", milestone)
                        .contains(NotificationChannel.EMAIL);
            }
        }
    }

    @Test
    void onlyTheEscalationMilestoneNotifiesTheLandlordByDefault() {
        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            RentReminderPolicy policy = RentReminderPolicy.defaultFor(tenantId, milestone);
            boolean expected = milestone == ReminderMilestone.OVERDUE_7;

            assertThat(policy.isNotifyLandlord())
                    .as("landlord notification at %s", milestone)
                    .isEqualTo(expected);
            assertThat(policy.landlordChannels().isEmpty())
                    .as("landlord channels at %s", milestone)
                    .isEqualTo(!expected);
        }
    }

    @Test
    void landlordIsOnlyEverTextedNotEmailed() {
        RentReminderPolicy policy =
                RentReminderPolicy.defaultFor(tenantId, ReminderMilestone.OVERDUE_7);

        assertThat(policy.landlordChannels()).containsExactly(NotificationChannel.SMS);
    }

    /**
     * The master switch has to beat the channel flags, otherwise turning a
     * milestone off would still send on whatever channels remained ticked.
     */
    @Test
    void disablingAMilestoneSilencesEveryChannelRegardlessOfChannelFlags() {
        RentReminderPolicy policy = RentReminderPolicy.rehydrate(
                UUID.randomUUID(), tenantId, ReminderMilestone.DUE_TODAY,
                false, true, true, true, true, null, null);

        assertThat(policy.renterChannels()).isEmpty();
        assertThat(policy.landlordChannels()).isEmpty();
    }

    @Test
    void storedPreferencesDriveTheChannelsWhenTheMilestoneIsEnabled() {
        RentReminderPolicy policy = RentReminderPolicy.rehydrate(
                UUID.randomUUID(), tenantId, ReminderMilestone.OVERDUE_3,
                true, true, false, true, false, null, null);

        assertThat(policy.renterChannels())
                .containsExactly(NotificationChannel.SMS, NotificationChannel.WHATSAPP);
        assertThat(policy.landlordChannels()).isEmpty();
    }
}
