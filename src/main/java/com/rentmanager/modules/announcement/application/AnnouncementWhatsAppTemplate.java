package com.rentmanager.modules.announcement.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Formats the WhatsApp leg of an announcement. WhatsApp Business API does
 * NOT allow free-form business-initiated messages outside the 24-hour
 * session window: every broadcast message must use a pre-approved
 * template. Design: one approved Utility-category template with a single
 * variable slot -
 *
 *   "New announcement from {{landlord_name}}: {{1}}. View full details in
 *    the RentManager app."
 *
 * The landlord's message is injected into {{1}}, truncated to the
 * character limit the approved template allows
 * ({@code app.announcement.whatsapp-max-chars}). The full, untruncated
 * text still reaches the renter via SMS, email and in-app - WhatsApp is
 * the pointer back into the app, never the carrier of the full text.
 */
@RequiredArgsConstructor
@Component
public class AnnouncementWhatsAppTemplate {

    public static final String MESSAGE_SLOT = "{{1}}";

    private final AnnouncementProperties properties;

    public String templateName() {
        return properties.getWhatsappTemplateName();
    }

    public String format(String landlordName, String message) {
        String body = truncate(message);
        return "New announcement from " + safe(landlordName) + ": " + body
                + ". View full details in the RentManager app.";
    }

    public String truncate(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= properties.getWhatsappMaxChars()) {
            return message;
        }
        String cut = message.substring(0, properties.getWhatsappMaxChars() - 1);
        return cut.substring(0, cut.lastIndexOf(' ') > 0 ? cut.lastIndexOf(' ') : cut.length()) + "…";
    }

    private String safe(String name) {
        return name == null || name.isBlank() ? "your landlord" : name.trim();
    }
}
