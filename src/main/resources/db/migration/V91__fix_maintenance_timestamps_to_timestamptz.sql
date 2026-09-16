-- TD-128: maintenance timestamps were stored as bare TIMESTAMP (no timezone).
-- On a UTC server a Nairobi landlord read them three hours early.
-- The column type is changed to TIMESTAMPTZ; existing values, which were
-- written as LocalDateTime.now() on a UTC JVM, are re-cast as UTC so their
-- meaning is preserved and no data is lost.
ALTER TABLE maintenance_requests
    ALTER COLUMN completed_at             TYPE TIMESTAMPTZ USING completed_at             AT TIME ZONE 'UTC',
    ALTER COLUMN first_landlord_response_at TYPE TIMESTAMPTZ USING first_landlord_response_at AT TIME ZONE 'UTC',
    ALTER COLUMN landlord_viewed_at       TYPE TIMESTAMPTZ USING landlord_viewed_at       AT TIME ZONE 'UTC';
