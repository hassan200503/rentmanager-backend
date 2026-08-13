package com.rentmanager.modules.integration.application.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;

/**
 * Real SMTP test: sends one actual email to the address the owner supplies
 * in the test dialog using the saved SMTP configuration.
 */
@Slf4j
@Component
public class EmailProviderTester implements ProviderTester {

    @Override
    public String providerKey() {
        return "email";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String host = credentials.getOrDefault("host", "");
        String port = credentials.getOrDefault("port", "587");
        String username = credentials.getOrDefault("username", "");
        String password = credentials.getOrDefault("password", "");
        String fromAddress = credentials.getOrDefault("from_address", "");

        if (host.isBlank()) {
            return TestResult.failure("Missing credentials", "SMTP Host is required before testing.");
        }
        if (target == null || target.isBlank()) {
            return TestResult.failure("Recipient required", "Type an email address to receive the test email.");
        }

        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(Integer.parseInt(port.trim()));
            if (!username.isBlank()) {
                sender.setUsername(username);
            }
            if (!password.isBlank()) {
                sender.setPassword(password);
            }
            Properties props = sender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            if (!username.isBlank()) {
                props.put("mail.smtp.auth", "true");
            }
            props.put("mail.smtp.starttls.enable", tlsEnabled(credentials));
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(target);
            if (!fromAddress.isBlank()) {
                message.setFrom(fromAddress);
            }
            message.setSubject("RentManager — Test Connection");
            message.setText("This email confirms your RentManager email integration settings are working.");
            sender.send(message);

            return TestResult.success("Test email accepted by the SMTP server for delivery to " + target + ".");
        } catch (Exception e) {
            log.warn("SMTP test connection failed for host={}", host, e);
            return TestResult.failure("SMTP send failed", String.valueOf(e.getMessage()));
        }
    }

    private static boolean tlsEnabled(Map<String, String> credentials) {
        String tls = credentials.getOrDefault("tls", "");
        return tls.isBlank() || "true".equalsIgnoreCase(tls);
    }
}