-- V54: landlord "unviewed requests" badge support.
--
-- landlord_viewed_at is set once when the landlord first sees a request
-- (Requests hub load, or any landlord status mutation). A request counts as
-- "unviewed" while this column is NULL, which is what drives the sidebar
-- badge count on the frontend.
--
-- Requests that existed before this feature shipped are backfilled to
-- created_at so the badge starts at 0 instead of flooding with historical
-- requests the landlord has long since seen.
ALTER TABLE maintenance_requests
    ADD COLUMN landlord_viewed_at TIMESTAMP;

UPDATE maintenance_requests
SET landlord_viewed_at = created_at
WHERE landlord_viewed_at IS NULL;

CREATE INDEX idx_maintenance_requests_landlord_viewed_at
    ON maintenance_requests (landlord_viewed_at);
