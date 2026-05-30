-- This migration version previously failed in deployed databases during the legacy consultant backfill.
-- Legacy consultant backfill has been abandoned: consultants are now created manually by super admin.
-- Keep version 20260528.09 as a safe no-op so Flyway can mark it successful after the failed history row is repaired.

SELECT 1;
