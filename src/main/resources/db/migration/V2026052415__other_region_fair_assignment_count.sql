ALTER TABLE consultant_region_assignment
    ADD COLUMN other_assign_count INT NOT NULL DEFAULT 0 AFTER priority;

UPDATE consultant_region_assignment
SET other_assign_count = 0
WHERE other_assign_count IS NULL;
