package com.rentmanager.modules.notification.email;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Production email implementation (Phase 5). Active only when
 * {@code app.notification.email.enabled=true} AND SMTP settings
 * (spring.mail.host etc.) are configured. Failures propagate to the
 * caller - the outbox turns them into retries with backoff.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.email", name = "enabled", havingValue = "true")
public class JavaMailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new RuntimeException("Email send failed", e);
        }
    }
}
