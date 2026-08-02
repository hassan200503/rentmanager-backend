package com.rentmanager.modules.notification.whatsapp;

/**
 * WhatsApp channel (Phase 5). The provider is a business decision that is
 * NOT made in code: today the only implementation is
 * {@link LoggingWhatsAppService}, so WhatsApp deliveries are logged and
 * skipped (degraded gracefully) until a provider is chosen and configured.
 */
public interface WhatsAppService {

    void send(String to, String message);

    /**
     * Template-based send (announcement broadcasts). WhatsApp Business API
     * only allows free-form business-initiated messages inside a 24-hour
     * session window; outside it every message must use a pre-approved
     * template. The default implementation falls back to the raw send for
     * provider stubs - a real provider must override this and send via the
     * template named by {@code templateName} (or fail loudly if the
     * template is not registered).
     *
     * @param templateName the registered template (may be null for
     *                     non-template flows)
     * @param message      the final formatted body
     */
    default void sendTemplate(String to, String templateName, String message) {
        send(to, message);
    }
}
