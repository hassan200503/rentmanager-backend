package com.rentmanager.modules.notification.application;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import com.rentmanager.modules.notification.email.EmailService;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.notification.whatsapp.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Phase 5: one failing row can never block the rest of the batch; SENT
 * rows are never dispatched again; GAVE_UP happens after exhaustion.
 */
class NotificationDispatchSweepServiceTest {

    private NotificationDeliveryRepository deliveryRepository;
    private NotificationDispatchService dispatchService;
    private SmsService smsService;
    private EmailService emailService;
    private WhatsAppService whatsAppService;
    private NotificationDispatchSweepService sweepService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(NotificationDeliveryRepository.class);
        smsService = mock(SmsService.class);
        emailService = mock(EmailService.class);
        whatsAppService = mock(WhatsAppService.class);
        dispatchService = new NotificationDispatchService(smsService, emailService, whatsAppService);
        sweepService = new NotificationDispatchSweepService(deliveryRepository, dispatchService);
    }

    @Test
    void marksSmsSent_whenProviderConfirms() {
        NotificationDelivery delivery = delivery(NotificationChannel.SMS, "+254712345678");
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(smsService.sendRaw(anyString(), anyString())).thenReturn(true);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(NotificationDeliveryStatus.SENT, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getAttemptCount());
    }

    @Test
    void marksSmsFailed_whenProviderDoesNotConfirm() {
        NotificationDelivery delivery = delivery(NotificationChannel.SMS, "+254712345678");
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(smsService.sendRaw(anyString(), anyString())).thenReturn(false);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(NotificationDeliveryStatus.FAILED, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getAttemptCount());
        assertNotNull(captor.getValue().getNextAttemptAt());
    }

    @Test
    void marksEmailSent_whenChannelAccepts() {
        NotificationDelivery delivery = delivery(NotificationChannel.EMAIL, "a@b.c");
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        doNothing().when(emailService).send(anyString(), anyString(), anyString());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        verify(emailService).send(eq("a@b.c"), anyString(), anyString());
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(NotificationDeliveryStatus.SENT, captor.getValue().getStatus());
    }

    @Test
    void whatsappChannelDegradesToSent_whenStubActive() {
        NotificationDelivery delivery = delivery(NotificationChannel.WHATSAPP, "+254712345678");
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        verify(whatsAppService).send(eq("+254712345678"), anyString());
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(NotificationDeliveryStatus.SENT, captor.getValue().getStatus());
    }

    @Test
    void recordsFailure_whenChannelThrows() {
        NotificationDelivery delivery = delivery(NotificationChannel.EMAIL, "a@b.c");
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        doThrow(new RuntimeException("smtp down")).when(emailService).send(anyString(), anyString(), anyString());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(NotificationDeliveryStatus.FAILED, captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("smtp down"));
    }

    @Test
    void failingRowDoesNotBlockOthersInBatch() {
        NotificationDelivery failing = delivery(NotificationChannel.EMAIL, "fail@b.c");
        NotificationDelivery succeeding = delivery(NotificationChannel.SMS, "+254700000000");

        when(deliveryRepository.findDue(any(Instant.class), anyInt()))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("smtp down")).when(emailService).send(anyString(), anyString(), anyString());
        when(smsService.sendRaw(anyString(), anyString())).thenReturn(true);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> sweepService.dispatchDue());

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).save(captor.capture());
        NotificationDelivery savedFailing = captor.getAllValues().get(0);
        NotificationDelivery savedSucceeding = captor.getAllValues().get(1);
        assertEquals(NotificationDeliveryStatus.FAILED, savedFailing.getStatus());
        assertEquals(NotificationDeliveryStatus.SENT, savedSucceeding.getStatus());
    }

    @Test
    void givesUpAfterThreeFailedSweeps() {
        NotificationDelivery delivery = delivery(NotificationChannel.EMAIL, "a@b.c");
        doThrow(new RuntimeException("smtp down")).when(emailService).send(anyString(), anyString(), anyString());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        for (int i = 0; i < 3; i++) {
            when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
            sweepService.dispatchDue();
        }

        assertEquals(NotificationDeliveryStatus.GAVE_UP, delivery.getStatus());
        assertEquals(3, delivery.getAttemptCount());
        assertFalse(delivery.isDue(Instant.now()));
    }

    @Test
    void failedDeliveryIsRedispatchedOnceBackoffElapsed() {
        NotificationDelivery failed = delivery(NotificationChannel.SMS, "+254712345678");
        doThrow(new RuntimeException("provider down")).when(smsService).sendRaw(anyString(), anyString());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(failed));

        sweepService.dispatchDue();
        assertEquals(NotificationDeliveryStatus.FAILED, failed.getStatus(), "first sweep fails");

        doReturn(true).when(smsService).sendRaw(anyString(), anyString());
        sweepService.dispatchDue();

        assertEquals(NotificationDeliveryStatus.SENT, failed.getStatus(), "FAILED row is retried by a later sweep");
        assertEquals(2, failed.getAttemptCount());
    }

    @Test
    void doesNothing_whenNoDueDeliveries() {
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of());

        sweepService.dispatchDue();

        verify(deliveryRepository, never()).save(any());
        verifyNoInteractions(smsService);
    }

    private NotificationDelivery delivery(NotificationChannel channel, String recipient) {
        return NotificationDelivery.create(
                tenantId, eventId, channel, recipient,
                channel == NotificationChannel.EMAIL ? "Subj" : null,
                "Message body", null);
    }
}
