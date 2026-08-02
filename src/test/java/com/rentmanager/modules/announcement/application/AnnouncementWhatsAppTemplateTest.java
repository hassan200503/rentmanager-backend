package com.rentmanager.modules.announcement.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WhatsApp leg is the one channel that cannot carry arbitrary free
 * text: it must go through the registered Utility template with the
 * landlord's message injected into the single variable slot, truncated to
 * whatever character limit the approved template allows.
 */
class AnnouncementWhatsAppTemplateTest {

    @Test
    void formatsTemplateWithLandlordNameAndMessage() {
        AnnouncementProperties properties = properties(500);
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties);

        String formatted = template.format("Acme Properties", "Water will be off on Sunday 8-11am");

        assertTrue(formatted.startsWith("New announcement from Acme Properties: Water will be off on Sunday 8-11am."));
        assertTrue(formatted.endsWith("View full details in the RentManager app."));
        assertEquals("announcement_utility_v1", template.templateName());
    }

    @Test
    void fallsBackToYourLandlordWhenNameMissing() {
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties(500));

        assertTrue(template.format(null, "Hello").startsWith("New announcement from your landlord: Hello."));
        assertTrue(template.format("   ", "Hello").startsWith("New announcement from your landlord: Hello."));
    }

    @Test
    void truncatesMessageToTemplateLimit() {
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties(20));

        String formatted = template.format("Acme", "This is a very long message that must be cut");

        assertTrue(formatted.length() <= "New announcement from Acme: ".length() + 20 + ". View full details in the RentManager app.".length());
        assertTrue(formatted.contains("…"));
        assertFalse(formatted.contains("must be cut"));
    }

    @Test
    void shortMessageIsNeverTruncated() {
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties(500));

        String message = "Lift maintenance Friday";
        assertEquals(message, template.truncate(message));
    }

    @Test
    void messageAtExactLimitIsNotTruncated() {
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties(10));

        assertEquals("1234567890", template.truncate("1234567890"));
    }

    private AnnouncementProperties properties(int maxChars) {
        AnnouncementProperties props = new AnnouncementProperties();
        props.setWhatsappMaxChars(maxChars);
        props.setWhatsappTemplateName("announcement_utility_v1");
        return props;
    }
}
