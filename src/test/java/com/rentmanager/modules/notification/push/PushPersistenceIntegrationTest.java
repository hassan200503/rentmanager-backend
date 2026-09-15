package com.rentmanager.modules.notification.push;

import com.rentmanager.modules.notification.push.application.NotificationPreferenceService;
import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.notification.push.domain.PushCategory;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres checks for the push tables (V97, V99, V100): the guarantees
 * here live in SQL — upserts, the unique token, ordering — which mocked
 * repositories cannot demonstrate.
 */
// Rolled back after each test: this suite shares one reusable Postgres
// container, and rows left behind break other suites' cleanup (e.g.
// TenantSaaSIntegrationTest deletes from tenants).
@org.springframework.transaction.annotation.Transactional
class PushPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PushDeviceService devices;
    @Autowired
    private NotificationPreferenceService preferences;
    @Autowired
    private PushTicketRepository tickets;

    private static String token() {
        return "ExponentPushToken[" + UUID.randomUUID().toString().replace("-", "") + "]";
    }

    @Test
    void aTokenHasOneOwnerAndMovesToWhoeverRegistersItLast() {
        String t = token();
        String alice = "user_" + UUID.randomUUID();
        String bob = "user_" + UUID.randomUUID();

        devices.register(alice, t, PushPlatform.ANDROID, "1.0.0");
        devices.register(bob, t, PushPlatform.ANDROID, "1.0.0");

        assertThat(devices.isDeliverable(t, bob)).isTrue();
        assertThat(devices.isDeliverable(t, alice)).isFalse();
        assertThat(devices.activeDevices(alice)).isEmpty();
        assertThat(devices.activeDevices(bob)).hasSize(1);
    }

    @Test
    void unregisterByAnotherPersonDoesNothing() {
        String t = token();
        String owner = "user_" + UUID.randomUUID();
        devices.register(owner, t, PushPlatform.IOS, null);

        devices.unregister("user_attacker", t);

        assertThat(devices.isDeliverable(t, owner)).isTrue();
    }

    @Test
    void preferencesDefaultOnAndUpsert() {
        String person = "user_" + UUID.randomUUID();
        assertThat(preferences.isPushEnabled(person, PushCategory.MAINTENANCE)).isTrue();

        preferences.update(person, Map.of(PushCategory.MAINTENANCE, false));
        preferences.update(person, Map.of(PushCategory.MAINTENANCE, false, PushCategory.RENT_PAYMENTS, true));

        assertThat(preferences.isPushEnabled(person, PushCategory.MAINTENANCE)).isFalse();
        assertThat(preferences.get(person)).containsEntry(PushCategory.RENT_PAYMENTS, true);

        preferences.deleteAll(person);
        assertThat(preferences.isPushEnabled(person, PushCategory.MAINTENANCE)).isTrue();
    }

    @Test
    void ticketsAreQueuedIdempotentlyAndDrained() {
        String id = "ticket-" + UUID.randomUUID();
        tickets.save(id, token());
        tickets.save(id, token()); // duplicate ticket id is ignored, not an error

        List<PushTicketRepository.PendingTicket> due = tickets.findCreatedBefore(Instant.now().plusSeconds(1), 1000);
        assertThat(due).extracting(PushTicketRepository.PendingTicket::ticketId).containsOnlyOnce(id);

        tickets.deleteAll(List.of(id));
        assertThat(tickets.findCreatedBefore(Instant.now().plusSeconds(1), 1000))
                .extracting(PushTicketRepository.PendingTicket::ticketId).doesNotContain(id);
    }
}
