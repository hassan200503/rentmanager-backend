package com.rentmanager.modules.notification.application;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.email.EmailService;
import com.rentmanager.modules.notification.push.application.PushNotificationService;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.notification.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Routes one outbox delivery to its channel (Phase 5). SMS failures are
 * reported as false (the provider contract), email failures throw - both
 * are surfaced to the caller so the sweep can record the attempt and
 * schedule a retry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final SmsService smsService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final PushNotificationService pushNotificationService;

    /**
     * @return true when the channel accepted the delivery, false when it
     *         reported a transient failure (caller records the retry).
     * @throws RuntimeException when the channel is misconfigured
     *         (caller records the retry too, then gives up on exhaustion).
     */
    public boolean dispatch(NotificationDelivery delivery) {
        return dispatch(
                delivery.getChannel(),
                delivery.getRecipient(),
                delivery.getSubject(),
                delivery.getMessage(),
                delivery.getMetadata()
        );
    }

    /**
     * Channel routing shared with the announcement broadcast sweep: one
     * router for every external channel, whatever the originating feature.
     * The WhatsApp leg is template-formatted BEFORE this call (the message
     * argument is the final templated body); the template name rides in
     * {@code metadata} for the provider implementation to use.
     */
    public boolean dispatch(
            NotificationChannel channel,
            String recipient,
            String subject,
            String message,
            String metadata
    ) {
        switch (channel) {
            case SMS -> {
                return smsService.sendRaw(recipient, message);
            }
            case EMAIL -> {
                emailService.send(
                        recipient,
                        subject == null ? "RentManager" : subject,
                        message);
                return true;
            }
            case WHATSAPP -> {
                if (metadata == null || metadata.isBlank()) {
                    whatsAppService.send(recipient, message);
                } else {
                    whatsAppService.sendTemplate(recipient, metadata, message);
                }
                return true;
            }
            case PUSH -> {
                return pushNotificationService.deliver(recipient, subject, message, metadata);
            }
            default -> throw new IllegalStateException("Unknown channel: " + channel);
        }
    }
}
