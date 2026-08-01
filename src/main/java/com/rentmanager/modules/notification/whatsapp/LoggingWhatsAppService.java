package com.rentmanager.modules.notification.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Graceful degradation for the WhatsApp channel (Phase 5): no provider is
 * wired yet, so sends are logged and skipped. The outbox records the
 * delivery as SENT - the pipeline worked, the transport is intentionally
 * a no-op until a WhatsApp provider is selected and configured. When a
 * provider arrives, implement it behind {@link WhatsAppService} and gate
 * this stub off via configuration.
 */
@Slf4j
@Service
public class LoggingWhatsAppService implements WhatsAppService {

    @Override
    public void send(String to, String message) {
        log.warn("[WHATSAPP STUB] Would send to {}: {} (no WhatsApp provider configured; skipping)",
                to, message);
    }
}
