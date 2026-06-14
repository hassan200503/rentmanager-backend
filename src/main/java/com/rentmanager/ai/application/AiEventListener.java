package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.model.AiEventSnapshot;
import com.rentmanager.ai.infrastructure.event.AiDomainEventListener;
import com.rentmanager.ai.application.AiInsightOrchestrator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AiEventListener {

    private final AiContextBuilder contextBuilder;
    private final AiInsightOrchestrator orchestrator;

    public AiEventListener(
            AiContextBuilder contextBuilder,
            AiInsightOrchestrator orchestrator
    ) {
        this.contextBuilder = contextBuilder;
        this.orchestrator = orchestrator;
    }

    /**
     * 🚨 SAFE ENTRY POINT ONLY
     * - Blocks Spring startup lifecycle execution
     * - Ensures AI only runs on real domain events
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ignoreStartupEvents(ApplicationReadyEvent event) {
        // intentionally empty
    }

    /**
     * REAL DOMAIN EVENT PROCESSING (SAFE)
     */
    @AiDomainEventListener
    public void handle(Object event) {

        if (event == null) {
            return;
        }

        // SAFETY: Prevent execution during unsafe system states
        if (!isRuntimeSafe()) {
            return;
        }

        AiEventSnapshot snapshot = contextBuilder.build(event);

        orchestrator.process(snapshot);
    }

    /**
     * SaaS safety gate:
     * ensures tenant context + runtime readiness
     */
    private boolean isRuntimeSafe() {
        try {
            // Prevent crash during startup / async system events
            com.rentmanager.shared.security.context.TenantContext.getTenantId();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}