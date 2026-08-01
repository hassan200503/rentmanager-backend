package com.rentmanager.modules.notification.email;

/**
 * Email channel (Phase 5). Implementations are selected by
 * {@code app.notification.email.enabled}: a real JavaMail-backed sender
 * when true, a logging stub otherwise. Delivery failures propagate to the
 * caller so the outbox can record them and retry.
 */
public interface EmailService {

    void send(String to, String subject, String body);
}
