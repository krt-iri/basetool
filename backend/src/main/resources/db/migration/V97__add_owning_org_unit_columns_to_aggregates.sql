-- =====================================================================
-- V96 - Spezialkommando R1 step 3: aggregate FK columns + indexes
-- =====================================================================
-- Why: SPEZIALKOMMANDO_PLAN.md §4 phase R1.b adds a second FK column on
-- every staffel-scoped aggregate that points at the new `org_unit`
-- table. The legacy `owning_squadron_id` / `creating_squadron_id` /
-- `requesting_squadron_id` columns stay in place during R1 and R2
-- (dual-write window); R3's V97-V100 will tighten NOT NULL on the new
-- columns and drop the legacy ones once prod confirms persistence.
--
-- Backfill is straightforward because V94 copied every squadron row
-- 1:1 into org_unit with the same UUIDs, so the new column's value is
-- simply the existing column's value. A `WHERE … IS NULL` guard makes
-- the UPDATE idempotent against re-runs.
--
-- promotion_topic is intentionally NOT touched here. Per §3.3 the
-- column there stays `owning_squadron_id` (typed Squadron only) — SK
-- rows can never own promotion data, and a future trigger-CHECK will
-- enforce that constraint. Including it in this migration would only
-- add noise; the column already targets `squadron(id)` (V85) and that
-- contract stays.
--
-- Indexes mirror V91's pattern (single-column index per owning-FK plus
-- the composite `mission(owning_squadron_id, is_internal)` analogue):
-- list endpoints will start filtering on the new column once R2 code
-- ships, and the planner needs the index ready on day one or the
-- mission and operation list pages will fall back to sequential scans.
--
-- Idempotency: every ADD COLUMN, ADD CONSTRAINT and CREATE INDEX uses
-- IF NOT EXISTS; UPDATEs are guarded by `WHERE … IS NULL`. Re-running
-- the migration on a developer DB that already had it applied is a
-- no-op.
--
-- Rollback: drop the new columns + indexes. Application code still
-- writes only the legacy columns at this stage; no production reader
-- depends on the new shape until R2.

-- ---------------------------------------------------------------------
-- 1. Mission
-- ---------------------------------------------------------------------
ALTER TABLE mission
    ADD COLUMN IF NOT EXISTS owning_org_unit_id UUID;

UPDATE mission
   SET owning_org_unit_id = owning_squadron_id
 WHERE owning_org_unit_id IS NULL
   AND owning_squadron_id IS NOT NULL;

ALTER TABLE mission
    DROP CONSTRAINT IF EXISTS fk_mission_owning_org_unit;
ALTER TABLE mission
    ADD  CONSTRAINT fk_mission_owning_org_unit
    FOREIGN KEY (owning_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_mission_owning_org_unit_id
    ON mission(owning_org_unit_id);

-- Composite index for the cross-staffel-public-escape clause
-- `WHERE owning_org_unit.id = :scope OR is_internal = false`; mirrors
-- V91's `idx_mission_owning_squadron_internal`.
CREATE INDEX IF NOT EXISTS idx_mission_owning_org_unit_internal
    ON mission(owning_org_unit_id, is_internal);

-- ---------------------------------------------------------------------
-- 2. Operation
-- ---------------------------------------------------------------------
ALTER TABLE operation
    ADD COLUMN IF NOT EXISTS owning_org_unit_id UUID;

UPDATE operation
   SET owning_org_unit_id = owning_squadron_id
 WHERE owning_org_unit_id IS NULL
   AND owning_squadron_id IS NOT NULL;

ALTER TABLE operation
    DROP CONSTRAINT IF EXISTS fk_operation_owning_org_unit;
ALTER TABLE operation
    ADD  CONSTRAINT fk_operation_owning_org_unit
    FOREIGN KEY (owning_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_operation_owning_org_unit_id
    ON operation(owning_org_unit_id);

-- ---------------------------------------------------------------------
-- 3. Ship
-- ---------------------------------------------------------------------
ALTER TABLE ship
    ADD COLUMN IF NOT EXISTS owning_org_unit_id UUID;

UPDATE ship
   SET owning_org_unit_id = owning_squadron_id
 WHERE owning_org_unit_id IS NULL
   AND owning_squadron_id IS NOT NULL;

ALTER TABLE ship
    DROP CONSTRAINT IF EXISTS fk_ship_owning_org_unit;
ALTER TABLE ship
    ADD  CONSTRAINT fk_ship_owning_org_unit
    FOREIGN KEY (owning_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_ship_owning_org_unit_id
    ON ship(owning_org_unit_id);

-- ---------------------------------------------------------------------
-- 4. InventoryItem
-- ---------------------------------------------------------------------
ALTER TABLE inventory_item
    ADD COLUMN IF NOT EXISTS owning_org_unit_id UUID;

UPDATE inventory_item
   SET owning_org_unit_id = owning_squadron_id
 WHERE owning_org_unit_id IS NULL
   AND owning_squadron_id IS NOT NULL;

ALTER TABLE inventory_item
    DROP CONSTRAINT IF EXISTS fk_inventory_item_owning_org_unit;
ALTER TABLE inventory_item
    ADD  CONSTRAINT fk_inventory_item_owning_org_unit
    FOREIGN KEY (owning_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_inventory_item_owning_org_unit_id
    ON inventory_item(owning_org_unit_id);

-- ---------------------------------------------------------------------
-- 5. RefineryOrder
-- ---------------------------------------------------------------------
ALTER TABLE refinery_order
    ADD COLUMN IF NOT EXISTS owning_org_unit_id UUID;

UPDATE refinery_order
   SET owning_org_unit_id = owning_squadron_id
 WHERE owning_org_unit_id IS NULL
   AND owning_squadron_id IS NOT NULL;

ALTER TABLE refinery_order
    DROP CONSTRAINT IF EXISTS fk_refinery_order_owning_org_unit;
ALTER TABLE refinery_order
    ADD  CONSTRAINT fk_refinery_order_owning_org_unit
    FOREIGN KEY (owning_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_refinery_order_owning_org_unit_id
    ON refinery_order(owning_org_unit_id);

-- ---------------------------------------------------------------------
-- 6. JobOrder (dual columns: creating + requesting)
-- ---------------------------------------------------------------------
ALTER TABLE job_order
    ADD COLUMN IF NOT EXISTS creating_org_unit_id   UUID;
ALTER TABLE job_order
    ADD COLUMN IF NOT EXISTS requesting_org_unit_id UUID;

UPDATE job_order
   SET creating_org_unit_id = creating_squadron_id
 WHERE creating_org_unit_id IS NULL
   AND creating_squadron_id IS NOT NULL;

UPDATE job_order
   SET requesting_org_unit_id = requesting_squadron_id
 WHERE requesting_org_unit_id IS NULL
   AND requesting_squadron_id IS NOT NULL;

ALTER TABLE job_order
    DROP CONSTRAINT IF EXISTS fk_job_order_creating_org_unit;
ALTER TABLE job_order
    ADD  CONSTRAINT fk_job_order_creating_org_unit
    FOREIGN KEY (creating_org_unit_id)   REFERENCES org_unit(id);

ALTER TABLE job_order
    DROP CONSTRAINT IF EXISTS fk_job_order_requesting_org_unit;
ALTER TABLE job_order
    ADD  CONSTRAINT fk_job_order_requesting_org_unit
    FOREIGN KEY (requesting_org_unit_id) REFERENCES org_unit(id);

CREATE INDEX IF NOT EXISTS idx_job_order_creating_org_unit_id
    ON job_order(creating_org_unit_id);
CREATE INDEX IF NOT EXISTS idx_job_order_requesting_org_unit_id
    ON job_order(requesting_org_unit_id);
