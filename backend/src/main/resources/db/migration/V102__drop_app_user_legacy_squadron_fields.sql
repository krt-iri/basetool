-- =============================================================================
-- SPEZIALKOMMANDO_PLAN.md §10 PR-7 / R8_DESTRUCTIVE_ROADMAP.md Step 5 — drop the legacy
-- per-user squadron + flag columns. After R9 Step 4 every reader consults
-- org_unit_membership instead.
--
-- *** REQUIRES R9 STEP 4 IN PROD + FULL DB BACKUP ***
-- Irreversible. The membership table is now the sole source of truth for "which Staffel does
-- this user belong to" and "does this user carry the Logistician / Mission Manager flag".
--
-- Pre-merge gate: confirm via
--   SELECT COUNT(*) FROM app_user u
--    WHERE u.squadron_id IS NOT NULL
--      AND NOT EXISTS (
--        SELECT 1 FROM org_unit_membership m
--         WHERE m.user_id = u.id AND m.kind = 'SQUADRON' AND m.org_unit_id = u.squadron_id)
-- returns 0 — every populated app_user.squadron_id has a matching membership row courtesy of
-- R6.e dual-write + V96 backfill.
-- =============================================================================

-- 1. Drop FK constraint first (V81 added it: fk_app_user_squadron).
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS fk_app_user_squadron;

-- 2. Drop the index (V91 added it: idx_app_user_squadron_id).
DROP INDEX IF EXISTS idx_app_user_squadron_id;

-- 3. Drop the columns.
ALTER TABLE app_user DROP COLUMN IF EXISTS squadron_id;
ALTER TABLE app_user DROP COLUMN IF EXISTS is_logistician;
ALTER TABLE app_user DROP COLUMN IF EXISTS is_mission_manager;
