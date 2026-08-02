package com.rentmanager.modules.announcement.domain.enums;

/**
 * Lifecycle of one announcement delivery (one active renter x one channel).
 *
 * <ul>
 *   <li>PENDING - queued for external dispatch.</li>
 *   <li>SENT - the provider accepted the message.</li>
 *   <li>DELIVERED - terminal, no external dispatch happened: IN_APP rows
 *       are DELIVERED at creation (the portal row IS the delivery), and
 *       the provider acceptance is recorded here for in-app reads via
 *       read_at.</li>
 *   <li>FAILED - transient provider failure; the sweep retries with
 *       backoff and gives up permanently after MAX_ATTEMPTS.</li>
 *   <li>SKIPPED_NO_OPTIN - terminal, WhatsApp only: the renter has no
 *       captured opt-in. Never attempted, never retried.</li>
 * </ul>
 */
public enum AnnouncementDeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    SKIPPED_NO_OPTIN
}
