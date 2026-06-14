package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.model.AiEventSnapshot;
import com.rentmanager.shared.security.context.TenantContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class AiContextBuilder {

    public AiEventSnapshot build(Object event) {

        if (event == null) {
            throw new IllegalArgumentException("AI event cannot be null");
        }

        UUID tenantId = resolveTenantSafely();

        return new AiEventSnapshot(
                tenantId,
                event.getClass().getSimpleName(),
                extractEntityId(event),
                Map.of(
                        "eventType", event.getClass().getSimpleName(),
                        "payload", safeSerialize(event)
                ),
                Instant.now()
        );
    }

    /**
     * SaaS-safe tenant resolution
     * - NEVER crashes system during startup / async execution
     * - Returns null if context is not available
     */
    private UUID resolveTenantSafely() {
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            return null; // AI must tolerate system-level events
        }
    }

    /**
     * Extract entity ID safely across all domain events
     */
    private UUID extractEntityId(Object event) {
        try {
            var method = event.getClass().getMethod("getId");
            Object result = method.invoke(event);

            if (result instanceof UUID uuid) {
                return uuid;
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safe serialization fallback (no runtime failure)
     */
    private String safeSerialize(Object event) {
        try {
            return event.toString();
        } catch (Exception e) {
            return "unserializable-event:" + event.getClass().getSimpleName();
        }
    }
}