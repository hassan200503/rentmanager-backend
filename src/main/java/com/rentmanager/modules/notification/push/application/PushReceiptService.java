package com.rentmanager.modules.notification.push.application;

import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository.PendingTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads Expo delivery receipts and revokes devices that no longer exist.
 *
 * Receipts become available a few minutes after sending, so only tickets at
 * least {@link #RECEIPT_DELAY} old are checked. A ticket with no receipt yet
 * is left for the next sweep; one older than {@link #RECEIPT_RETENTION} is
 * discarded because Expo no longer holds it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushReceiptService {

    static final Duration RECEIPT_DELAY = Duration.ofMinutes(15);
    static final Duration RECEIPT_RETENTION = Duration.ofHours(24);
    /** Expo accepts up to 1000 ids per receipt request. */
    static final int BATCH = 500;

    private final PushTicketRepository tickets;
    private final PushSender sender;
    private final PushDeviceService devices;
    private final Clock clock;

    @Scheduled(fixedRateString = "${push.receipts.fixed-rate-ms:900000}", initialDelay = 120_000)
    public void sweep() {
        try {
            processOnce();
        } catch (Exception e) {
            log.warn("Push receipt sweep failed; tickets kept for the next sweep: {}", e.getClass().getSimpleName());
        }
    }

    /** @return number of devices revoked */
    public int processOnce() {
        Instant now = clock.instant();
        int expired = tickets.deleteCreatedBefore(now.minus(RECEIPT_RETENTION));
        if (expired > 0) {
            log.info("Discarded {} push tickets older than Expo's receipt retention", expired);
        }

        List<PendingTicket> due = tickets.findCreatedBefore(now.minus(RECEIPT_DELAY), BATCH);
        if (due.isEmpty()) {
            return 0;
        }

        Map<String, PushSender.ReceiptStatus> receipts =
                sender.receipts(due.stream().map(PendingTicket::ticketId).toList());

        int revoked = 0;
        List<String> done = new ArrayList<>();
        for (PendingTicket ticket : due) {
            PushSender.ReceiptStatus status = receipts.get(ticket.ticketId());
            if (status == null) {
                continue; // not ready yet
            }
            if (status == PushSender.ReceiptStatus.DEVICE_GONE) {
                devices.revokeDead(ticket.pushToken());
                revoked++;
            }
            done.add(ticket.ticketId());
        }
        tickets.deleteAll(done);
        if (revoked > 0) {
            log.info("Revoked {} push devices reported as no longer registered", revoked);
        }
        return revoked;
    }
}
