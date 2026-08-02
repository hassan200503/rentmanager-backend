package com.rentmanager.modules.announcement.api.dto;

/**
 * Unread in-app announcements for the renter sidebar badge. count is the
 * number of IN_APP delivery rows with read_at NULL whose announcement has
 * not expired.
 */
public record AnnouncementUnreadCountResponse(long count) {
}
