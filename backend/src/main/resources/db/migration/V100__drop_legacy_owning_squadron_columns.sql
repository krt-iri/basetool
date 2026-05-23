-- =============================================================================
-- SPEZIALKOMMANDO_PLAN.md §10 PR-7 / R3.b + R8_DESTRUCTIVE_ROADMAP.md Step 3 — drop the
-- legacy owning_squadron_id columns + JobOrder's two creating/requesting_squadron_id columns
-- on the staffel-scoped aggregates. After R9 Step 1 the service-layer no longer writes them
-- directly (uses owning_org_unit_id), after Step 2 the entity dual-write lifecycle hook is
-- removed, and V99 already relaxed the NOT NULL constraint, so dropping the columns is now
-- safe.
--
-- *** REQUIRES R9 STEPS 1 + 2 IN PROD + FULL DB BACKUP ***
-- This migration is irreversible — once dropped, the legacy data is gone. Backfill from
-- owning_org_unit_id is possible only when the org_unit row carries kind='SQUADRON' (i.e.
-- the Squadron-only rows can be restored by joining on org_unit).
--
-- promotion_topic.owning_squadron_id is intentionally NOT touched — promotion data stays
-- Squadron-only per Plan §3.3 + the V98 guard trigger.
-- =============================================================================

-- 1. Drop foreign-key constraints first to avoid ALTER COLUMN ordering issues. The names
-- match the constraints introduced by V82 (aggregate-root squadron scope) and V83 (job_order
-- dual squadron columns).
ALTER TABLE mission        DROP CONSTRAINT IF EXISTS fk_mission_owning_squadron;
ALTER TABLE operation      DROP CONSTRAINT IF EXISTS fk_operation_owning_squadron;
ALTER TABLE ship           DROP CONSTRAINT IF EXISTS fk_ship_owning_squadron;
ALTER TABLE inventory_item DROP CONSTRAINT IF EXISTS fk_inventory_item_owning_squadron;
ALTER TABLE refinery_order DROP CONSTRAINT IF EXISTS fk_refinery_order_owning_squadron;
ALTER TABLE job_order      DROP CONSTRAINT IF EXISTS fk_job_order_creating_squadron;
ALTER TABLE job_order      DROP CONSTRAINT IF EXISTS fk_job_order_requesting_squadron;

-- 2. Drop the legacy indexes (V91 created them as idx_<table>_owning_squadron_id, plus
-- the composite (owning_squadron_id, is_internal) on mission; V93 added the three-column
-- (owning_squadron_id, is_internal, status) covering index for the mission search hot path).
DROP INDEX IF EXISTS idx_mission_owning_squadron_id;
DROP INDEX IF EXISTS idx_operation_owning_squadron_id;
DROP INDEX IF EXISTS idx_ship_owning_squadron_id;
DROP INDEX IF EXISTS idx_inventory_item_owning_squadron_id;
DROP INDEX IF EXISTS idx_refinery_order_owning_squadron_id;
DROP INDEX IF EXISTS idx_job_order_creating_squadron_id;
DROP INDEX IF EXISTS idx_job_order_requesting_squadron_id;
DROP INDEX IF EXISTS idx_mission_owning_squadron_internal;
DROP INDEX IF EXISTS idx_mission_squadron_internal_status;

-- 3. Drop the columns themselves.
ALTER TABLE mission        DROP COLUMN IF EXISTS owning_squadron_id;
ALTER TABLE operation      DROP COLUMN IF EXISTS owning_squadron_id;
ALTER TABLE ship           DROP COLUMN IF EXISTS owning_squadron_id;
ALTER TABLE inventory_item DROP COLUMN IF EXISTS owning_squadron_id;
ALTER TABLE refinery_order DROP COLUMN IF EXISTS owning_squadron_id;
ALTER TABLE job_order      DROP COLUMN IF EXISTS creating_squadron_id;
ALTER TABLE job_order      DROP COLUMN IF EXISTS requesting_squadron_id;
