package com.rentmanager.modules.notification.push.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Expo tickets awaiting a receipt (V99). */
public interface PushTicketRepository {

    record PendingTicket(String ticketId, String pushToken, Instant createdAt) {}

    void save(String ticketId, String pushToken);

    /** Oldest first, created at or before {@code cutoff}. */
    List<PendingTicket> findCreatedBefore(Instant cutoff, int limit);

    void deleteAll(Collection<String> ticketIds);

    int deleteCreatedBefore(Instant cutoff);
}
