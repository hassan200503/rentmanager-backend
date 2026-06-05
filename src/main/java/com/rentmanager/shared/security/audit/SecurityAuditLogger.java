package com.rentmanager.shared.security.audit;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SecurityAuditLogger {

    public void log(String action, String user, String tenant) {

        System.out.println("""
                [SECURITY AUDIT]
                time: %s
                action: %s
                user: %s
                tenant: %s
                """.formatted(
                Instant.now(),
                action,
                user,
                tenant
        ));
    }
}