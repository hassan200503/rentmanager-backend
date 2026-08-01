package com.rentmanager.modules.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Fallback email implementation for environments without SMTP configured.
 * Logs instead of sending; the outbox still records it as SENT because the
 * delivery pipeline is working - only the transport is stubbed.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.notification.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void send(String to, String subject, String body) {
        log.warn("[EMAIL STUB] Would send '{}' to {} (app.notification.email.enabled is false)",
                subject, to);
    }
}
