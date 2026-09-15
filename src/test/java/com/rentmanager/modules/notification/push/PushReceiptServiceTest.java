package com.rentmanager.modules.notification.push;

import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.notification.push.application.PushReceiptService;
import com.rentmanager.modules.notification.push.application.PushSender;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository.PendingTicket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PushReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    private PushTicketRepository tickets;
    private PushSender sender;
    private PushDeviceService devices;
    private PushReceiptService service;

    @BeforeEach
    void setUp() {
        tickets = mock(PushTicketRepository.class);
        sender = mock(PushSender.class);
        devices = mock(PushDeviceService.class);
        service = new PushReceiptService(tickets, sender, devices, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void revokesDeadDevicesAndClearsOnlyTicketsWithReceipts() {
        List<PendingTicket> due = List.of(
                new PendingTicket("t-ok", "ExponentPushToken[aaaaaaaaaaaa]", NOW.minusSeconds(3600)),
                new PendingTicket("t-gone", "ExponentPushToken[bbbbbbbbbbbb]", NOW.minusSeconds(3600)),
                new PendingTicket("t-pending", "ExponentPushToken[cccccccccccc]", NOW.minusSeconds(1000)));
        when(tickets.findCreatedBefore(any(), anyInt())).thenReturn(due);
        when(sender.receipts(anyCollection())).thenReturn(Map.of(
                "t-ok", PushSender.ReceiptStatus.DELIVERED,
                "t-gone", PushSender.ReceiptStatus.DEVICE_GONE));

        int revoked = service.processOnce();

        assertThat(revoked).isEqualTo(1);
        verify(devices).revokeDead("ExponentPushToken[bbbbbbbbbbbb]");
        verify(devices, never()).revokeDead("ExponentPushToken[aaaaaaaaaaaa]");
        verify(tickets).deleteAll(argThat(ids -> ids.containsAll(List.of("t-ok", "t-gone")) && !ids.contains("t-pending")));
    }

    @Test
    void onlyChecksTicketsOldEnoughAndExpiresStaleOnes() {
        when(tickets.findCreatedBefore(any(), anyInt())).thenReturn(List.of());

        service.processOnce();

        verify(tickets).deleteCreatedBefore(NOW.minusSeconds(24 * 3600));
        verify(tickets).findCreatedBefore(eq(NOW.minusSeconds(15 * 60)), anyInt());
        verifyNoInteractions(sender);
    }

    @Test
    void transportFailureKeepsTicketsForNextSweep() {
        when(tickets.findCreatedBefore(any(), anyInt())).thenReturn(List.of(
                new PendingTicket("t1", "ExponentPushToken[aaaaaaaaaaaa]", NOW.minusSeconds(3600))));
        when(sender.receipts(anyCollection())).thenThrow(new RuntimeException("network"));

        service.sweep();

        verify(tickets, never()).deleteAll(anyCollection());
        verifyNoInteractions(devices);
    }
}
