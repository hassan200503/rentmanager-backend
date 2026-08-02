package com.rentmanager.modules.announcement.domain.enums;

/**
 * Announcement severity. Mirrors the maintenance-request badge language
 * (Medium/High/Immediate) for visual consistency on the landlord side:
 * INFO renders as the neutral/info badge, URGENT as the warning badge.
 */
public enum AnnouncementPriority {
    INFO,
    URGENT
}
