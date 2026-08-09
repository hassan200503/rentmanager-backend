package com.rentmanager.modules.review.domain.enums;

/**
 * Moderation state of a platform review.
 *
 * <p>Every submitted review starts {@code PENDING}. Only {@code APPROVED}
 * reviews are eligible for any public surface (listing pages and the
 * platform testimonials feed). {@code HIDDEN} removes a published review
 * from public surfaces without deleting the record — an audit trail is
 * preserved while the content becomes invisible.</p>
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    HIDDEN
}