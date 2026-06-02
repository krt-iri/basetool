-- =============================================================================
-- Privacy / data minimisation (GDPR) -- PHASE 2 of the first/last-name removal.
--
-- Phase 1 (V133, previous release) scrubbed app_user.first_name / last_name to
-- NULL and removed the fields from the User JPA entity. After at least one prod
-- deploy on that release, this migration drops the now-unused, always-NULL
-- columns for good.
--
-- *** REQUIRES THE V133 RELEASE (entity no longer maps these columns) IN PROD ***
-- Irreversible without a DB backup. No reader remains: the columns have been
-- unmapped since V133, so ddl-auto=validate stays green after the drop. Nothing
-- references them -- they were plain nullable VARCHAR(255) with no FK, index or
-- constraint since V1 -- so a bare DROP COLUMN is sufficient. IF EXISTS keeps the
-- migration robust against a partially-applied developer database.
-- =============================================================================

ALTER TABLE app_user DROP COLUMN IF EXISTS first_name;
ALTER TABLE app_user DROP COLUMN IF EXISTS last_name;
