package com.rentmanager.modules.announcement.api.dto;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;

/**
 * The recipient-count preview shown to the landlord BEFORE confirming a
 * send - e.g. "This will reach 47 renters via in-app, SMS, and email
 * (3 skipped for WhatsApp - no opt-in on file)". SMS and WhatsApp cost
 * real money per message, so the landlord always sees the exposure before
 * committing. Computed from the same fan-out planner the broadcast job
 * executes, so the confirmed numbers always match the executed ones.
 */
public record AnnouncementPreviewResponse(
        int totalActiveRenters,
        int inApp,
        int sms,
        int email,
        int whatsapp,
        int whatsappSkipped
) {
    public int forChannel(AnnouncementChannel channel) {
        return switch (channel) {
            case IN_APP -> inApp;
            case SMS -> sms;
            case EMAIL -> email;
            case WHATSAPP -> whatsapp;
        };
    }
}
