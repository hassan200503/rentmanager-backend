ALTER TABLE units ADD COLUMN vacated_at TIMESTAMP NULL;

UPDATE units SET vacated_at = NOW() WHERE occupancy_status = 'VACANT' AND vacated_at IS NULL;