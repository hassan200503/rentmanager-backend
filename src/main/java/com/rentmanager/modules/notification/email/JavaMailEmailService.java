package com.rentmanager.modules.notification.email;

import com.rentmanager.modules.integration.application.IntegrationNotConfiguredException;
import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;

/**
 * Production email implementation (Phase 5). Active only when
 * {@code app.notification.email.enabled=true}.
 *
 * <p>SMTP settings are resolved through the {@link IntegrationRegistry} —
 * the encrypted, per-environment config the Owner enters in the
 * Integrations Console — falling back to the legacy {@code spring.mail.*}
 * environment properties while no console config exists. The
 * {@link JavaMailSender} is built on demand from the resolved credentials
 * (mirroring {@code EmailProviderTester}) rather than injected as a bean, so
 * enabling this flag can no longer fail application startup. Failures
 * propagate to the caller — the outbox turns them into bounded retries with
 * backoff (max {@code NotificationDelivery.MAX_ATTEMPTS}); an email is never
 * silently dropped and the channel never retries forever.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.notification.email", name = "enabled", havingValue = "true")
public class JavaMailEmailService implements EmailService {

    private final IntegrationRegistry registry;

    public JavaMailEmailService(IntegrationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void send(String to, String subject, String body) {
        IntegrationRegistry.ResolvedConfig resolved = registry.resolveOrNull(ProviderCatalog.EMAIL);
        if (resolved == null) {
            throw new IntegrationNotConfiguredException(ProviderCatalog.EMAIL);
        }
        Map<String, String> credentials = resolved.credentials();
        String host = value(credentials.get("host"));
        if (host.isBlank()) {
            throw new IllegalStateException(
                    "Email is enabled but the resolved SMTP configuration has no host. "
                            + "Set the SMTP Host in the Integrations Console or spring.mail.host.");
        }
        try {
            JavaMailSender sender = buildMailSender(credentials);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            String fromAddress = value(credentials.get("from_address"));
            if (!fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            sender.send(message);
            log.debug("Email sent to {} via SMTP host {} (source: {})",
                    to, host, resolved.fromDatabase() ? "console" : "environment");
        } catch (MailException e) {
            throw new RuntimeException("Email send failed", e);
        }
    }

    /**
     * Builds a JavaMailSender from the resolved SMTP credentials. Package-private
     * so tests can assert the transport wiring without opening a socket.
     */
    JavaMailSender buildMailSender(Map<String, String> credentials) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(value(credentials.get("host")));
        sender.setPort(parsePort(value(credentials.get("port")), 587));
        String username = value(credentials.get("username"));
        if (!username.isBlank()) {
            sender.setUsername(username);
        }
        String password = value(credentials.get("password"));
        if (!password.isBlank()) {
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        if (!username.isBlank()) {
            props.put("mail.smtp.auth", "true");
        }
        props.put("mail.smtp.starttls.enable", String.valueOf(tlsEnabled(credentials)));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        return sender;
    }

    private static boolean tlsEnabled(Map<String, String> credentials) {
        String tls = value(credentials.get("tls"));
        return tls.isBlank() || "true".equalsIgnoreCase(tls);
    }

    private static int parsePort(String port, int defaultPort) {
        if (port.isBlank()) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(port.trim());
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
