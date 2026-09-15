package com.rentmanager.modules.notification.push.application;

import java.util.Collection;
import java.util.Map;

/** Provider port for delivering push messages and reading their receipts. */
public interface PushSender {

    enum Result {
        /** The provider accepted the message. */
        ACCEPTED,
        /** Worth retrying later (network, provider 5xx, rate limit). */
        TRANSIENT_FAILURE,
        /** The token will never work again; stop sending to it. */
        DEVICE_GONE
    }

    /**
     * @param ticketId provider ticket for later receipt lookup; null unless ACCEPTED
     */
    record SendOutcome(Result result, String ticketId) {
        public static SendOutcome of(Result result) {
            return new SendOutcome(result, null);
        }
    }

    enum ReceiptStatus {
        /** Delivered to Apple/Google. */
        DELIVERED,
        /** The device is gone; revoke its token. */
        DEVICE_GONE,
        /** Some other delivery error; nothing to act on. */
        OTHER_ERROR
    }

    SendOutcome send(String pushToken, String title, String body, Map<String, String> data);

    /**
     * Receipts for the given ticket ids. Tickets whose receipt is not ready yet
     * are simply absent from the result. Throws on transport failure so the
     * caller keeps the tickets for the next sweep.
     */
    Map<String, ReceiptStatus> receipts(Collection<String> ticketIds);
}
