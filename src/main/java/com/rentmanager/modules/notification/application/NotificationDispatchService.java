package com.rentmanager.modules.notification.application;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.email.EmailService;
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

    /**
     * @return true when the channel accepted the delivery, false when it
     *         reported a transient failure (caller records the retry).
     * @throws RuntimeException when the channel is misconfigured
     *         (caller records the retry too, then gives up on exhaustion).
     */
    public boolean dispatch(NotificationDelivery delivery) {
        switch (delivery.getChannel()) {
            case SMS -> {
                return smsService.sendRaw(delivery.getRecipient(), delivery.getMessage());
            }
            case EMAIL -> {
                emailService.send(
                        delivery.getRecipient(),
                        delivery.getSubject() == null ? "RentManager" : delivery.getSubject(),
                        delivery.getMessage());
                return true;
            }
            case WHATSAPP -> {
                whatsAppService.send(delivery.getRecipient(), delivery.getMessage());
                return true;
            }
            default -> throw new IllegalStateException("Unknown channel: " + delivery.getChannel());
        }
    }
}
