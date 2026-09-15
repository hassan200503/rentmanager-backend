package com.rentmanager.modules.notification.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import com.rentmanager.modules.notification.push.application.NotificationPreferenceService;
import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.notification.push.application.PushNotificationService;
import com.rentmanager.modules.notification.push.application.PushSender;
import com.rentmanager.modules.notification.push.domain.PushCategory;
import com.rentmanager.modules.notification.push.domain.PushDevice;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushNotificationServiceTest {

    private static final String TOKEN_1 = "ExponentPushToken[device-one-aaaa]";
    private static final String TOKEN_2 = "ExpoPushToken[device-two-bbbbbb]";

    private PushDeviceService deviceService;
    private PushSender sender;
    private NotificationDeliveryRepository deliveryRepository;
    private NotificationPreferenceService preferences;
    private PushTicketRepository tickets;
    private PushNotificationService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        deviceService = mock(PushDeviceService.class);
        sender = mock(PushSender.class);
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        preferences = mock(NotificationPreferenceService.class);
        tickets = mock(PushTicketRepository.class);
        service = new PushNotificationService(deviceService, sender, deliveryRepository, objectMapper, preferences, tickets);
    }

    private String meta(String owner, Map<String, String> data) throws Exception {
        return objectMapper.writeValueAsString(Map.of("owner", owner, "data", data));
    }

    @Test
    void enqueuesOnePushDeliveryPerActiveDevice() {
        PushDevice d1 = PushDevice.register("user_a", TOKEN_1, PushPlatform.IOS, null);
        PushDevice d2 = PushDevice.register("user_a", TOKEN_2, PushPlatform.ANDROID, null);
        when(preferences.isPushEnabled("user_a", PushCategory.RENT_PAYMENTS)).thenReturn(true);
        when(deviceService.activeDevices("user_a")).thenReturn(List.of(d1, d2));

        int queued = service.enqueueForPerson(UUID.randomUUID(), UUID.randomUUID(), "user_a", PushCategory.RENT_PAYMENTS,
                "Payment recorded", "Tap to view", Map.of("type", "rent_payment_recorded", "id", "x"));

        assertThat(queued).isEqualTo(2);
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(d -> {
            assertThat(d.getChannel()).isEqualTo(NotificationChannel.PUSH);
            assertThat(d.getMetadata()).contains("\"owner\":\"user_a\"");
        });
    }

    @Test
    void respectsAPersonTurningACategoryOff() {
        when(preferences.isPushEnabled("user_a", PushCategory.MAINTENANCE)).thenReturn(false);

        int queued = service.enqueueForPerson(UUID.randomUUID(), UUID.randomUUID(), "user_a", PushCategory.MAINTENANCE,
                "t", "b", Map.of());

        assertThat(queued).isZero();
        verifyNoInteractions(deliveryRepository);
        verify(deviceService, never()).activeDevices(any());
    }

    @Test
    void preferenceLookupFailureDoesNotSilenceTheNotification() {
        PushDevice d1 = PushDevice.register("user_a", TOKEN_1, PushPlatform.IOS, null);
        when(preferences.isPushEnabled(any(), any())).thenThrow(new RuntimeException("db down"));
        when(deviceService.activeDevices("user_a")).thenReturn(List.of(d1));

        assertThat(service.enqueueForPerson(UUID.randomUUID(), UUID.randomUUID(), "user_a", PushCategory.MAINTENANCE,
                "t", "b", Map.of())).isEqualTo(1);
    }

    @Test
    void noRecipientQueuesNothing() {
        assertThat(service.enqueueForPerson(UUID.randomUUID(), UUID.randomUUID(), null, PushCategory.MAINTENANCE, "t", "b", Map.of()))
                .isZero();
        verifyNoInteractions(deliveryRepository);
    }

    @Test
    void dropsDeliveryWhenDeviceChangedOwnerSinceEnqueue() throws Exception {
        when(deviceService.isDeliverable(TOKEN_1, "user_a")).thenReturn(false);

        assertThat(service.deliver(TOKEN_1, "t", "b", meta("user_a", Map.of()))).isTrue();
        verifyNoInteractions(sender);
    }

    @Test
    void dropsDeliveryWithoutOwnerMetadata() {
        assertThat(service.deliver(TOKEN_1, "t", "b", null)).isTrue();
        verifyNoInteractions(sender);
    }

    @Test
    void sendsDeepLinkDataAndStoresTheTicketForReceiptChecking() throws Exception {
        when(deviceService.isDeliverable(TOKEN_1, "user_a")).thenReturn(true);
        when(sender.send(eq(TOKEN_1), anyString(), anyString(), anyMap()))
                .thenReturn(new PushSender.SendOutcome(PushSender.Result.ACCEPTED, "ticket-123"));

        assertThat(service.deliver(TOKEN_1, "t", "b",
                meta("user_a", Map.of("type", "renter_maintenance_updated", "id", "42")))).isTrue();

        verify(sender).send(TOKEN_1, "t", "b", Map.of("type", "renter_maintenance_updated", "id", "42"));
        verify(tickets).save("ticket-123", TOKEN_1);
    }

    @Test
    void transientFailureAsksForRetry() throws Exception {
        when(deviceService.isDeliverable(TOKEN_1, "user_a")).thenReturn(true);
        when(sender.send(any(), any(), any(), anyMap())).thenReturn(PushSender.SendOutcome.of(PushSender.Result.TRANSIENT_FAILURE));

        assertThat(service.deliver(TOKEN_1, "t", "b", meta("user_a", Map.of()))).isFalse();
        verifyNoInteractions(tickets);
    }

    @Test
    void deadDeviceIsRevokedAndNotRetried() throws Exception {
        when(deviceService.isDeliverable(TOKEN_1, "user_a")).thenReturn(true);
        when(sender.send(any(), any(), any(), anyMap())).thenReturn(PushSender.SendOutcome.of(PushSender.Result.DEVICE_GONE));

        assertThat(service.deliver(TOKEN_1, "t", "b", meta("user_a", Map.of()))).isTrue();
        verify(deviceService).revokeDead(TOKEN_1);
    }
}
