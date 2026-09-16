package com.rentmanager.modules.notification.push.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import com.rentmanager.modules.notification.push.domain.PushCategory;
import com.rentmanager.modules.notification.push.domain.PushDevice;
import com.rentmanager.modules.notification.push.domain.PushTicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enqueues push messages into the existing notification outbox — one
 * delivery row per device, so retries, backoff and give-up are the same
 * machinery every other channel already uses — and delivers them.
 *
 * <h3>Content rule</h3>
 * Push text is visible on a locked screen. Callers pass generic copy: no
 * amounts, names, phone numbers, request titles or landlord notes. The
 * {@code data} map carries only a type and an opaque id for deep linking;
 * the app fetches the real content after the user is authenticated, and the
 * backend authorises that fetch like any other request.
 */
@Slf4j
@Service
public class PushNotificationService {

    static final String META_OWNER = "owner";
    static final String META_DATA = "data";

    private final PushDeviceService deviceService;
    private final PushSender sender;
    private final NotificationDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final NotificationPreferenceService preferences;
    private final PushTicketRepository tickets;

    public PushNotificationService(
            PushDeviceService deviceService,
            PushSender sender,
            NotificationDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper,
            NotificationPreferenceService preferences,
            PushTicketRepository tickets
    ) {
        this.deviceService = deviceService;
        this.sender = sender;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
        this.preferences = preferences;
        this.tickets = tickets;
    }

    /** Queues one delivery per active device of the person. Never throws. */
    public int enqueueForPerson(
            UUID tenantId,
            UUID eventId,
            String clerkUserId,
            PushCategory category,
            String title,
            String body,
            Map<String, String> data
    ) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            return 0;
        }
        try {
            if (category != null && !preferences.isPushEnabled(clerkUserId, category)) {
                return 0;
            }
        } catch (Exception ex) {
            // Preference lookup failing must not silence a notification.
            log.warn("Push preference lookup failed; sending anyway: {}", ex.getClass().getSimpleName());
        }
        int queued = 0;
        try {
            String metadata = objectMapper.writeValueAsString(Map.of(
                    META_OWNER, clerkUserId,
                    META_DATA, data == null ? Map.of() : data));
            for (PushDevice device : deviceService.activeDevices(clerkUserId)) {
                try {
                    deliveryRepository.save(NotificationDelivery.create(
                            tenantId, eventId, NotificationChannel.PUSH,
                            device.getPushToken(), title, body, metadata));
                    queued++;
                } catch (Exception ex) {
                    log.error("Failed to enqueue push delivery for device {}: {}", device.getId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to enqueue push for event {}: {}", eventId, ex.getMessage());
        }
        return queued;
    }

    /**
     * Delivers one queued push. Returns true when the delivery is finished
     * (sent, or deliberately dropped), false when it should be retried.
     */
    public boolean deliver(String pushToken, String title, String body, String metadata) {
        Map<String, Object> meta = parse(metadata);
        Object owner = meta.get(META_OWNER);

        // Re-checked at send time, not only at enqueue: between the two the
        // person may have signed out, or someone else may have signed in on
        // the device. Either way this message is no longer theirs to see.
        if (!(owner instanceof String ownerId) || !deviceService.isDeliverable(pushToken, ownerId)) {
            log.info("Dropping push delivery: device revoked or reassigned since enqueue");
            return true;
        }

        PushSender.SendOutcome outcome = sender.send(pushToken, title, body, dataOf(meta));
        return switch (outcome.result()) {
            case ACCEPTED -> {
                if (outcome.ticketId() != null) {
                    try {
                        tickets.save(outcome.ticketId(), pushToken);
                    } catch (Exception ex) {
                        // Sent successfully; only the later receipt check is lost.
                        log.warn("Could not store push ticket: {}", ex.getClass().getSimpleName());
                    }
                }
                yield true;
            }
            case DEVICE_GONE -> {
                deviceService.revokeDead(pushToken);
                yield true;
            }
            case TRANSIENT_FAILURE -> false;
        };
    }

    private Map<String, Object> parse(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static Map<String, String> dataOf(Map<String, Object> meta) {
        Map<String, String> out = new LinkedHashMap<>();
        if (meta.get(META_DATA) instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(k.toString(), v.toString());
                }
            });
        }
        return out;
    }
}
