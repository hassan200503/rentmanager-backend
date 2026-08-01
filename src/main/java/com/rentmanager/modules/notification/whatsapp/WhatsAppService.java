package com.rentmanager.modules.notification.whatsapp;

/**
 * WhatsApp channel (Phase 5). The provider is a business decision that is
 * NOT made in code: today the only implementation is
 * {@link LoggingWhatsAppService}, so WhatsApp deliveries are logged and
 * skipped (degraded gracefully) until a provider is chosen and configured.
 */
public interface WhatsAppService {

    void send(String to, String message);
}
