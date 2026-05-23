-- =============================================================================
-- SPEZIALKOMMANDO_PLAN.md §10 PR-6 (R3.a) — NOT NULL tighten on the new
-- `owning_org_unit_id` columns + DROP NOT NULL on the legacy
-- `owning_squadron_id` columns.
--
-- *** REQUIRES R6.e DUAL-WRITE SOAK COMPLETION + FULL DB BACKUP ***
-- This migration is "irreversible in practice" per the plan §10 risk register:
-- once the legacy NOT NULL is dropped, mixed-state rows (legacy null, new
-- populated) become representable, and a rollback of the application code to
-- R6.e or earlier would crash on inserts that omit the legacy column.
--
-- Pre-merge gate: confirm via `SELECT COUNT(*) FROM <aggregate> WHERE
-- owning_org_unit_id IS NULL` returns 0 on every staffel-scoped table — the
-- R4 dual-write hook should have populated the new column on every existing
-- and every newly-inserted row by R6.e soak end.
--
-- This migration also lifts the soft application-layer SK-ownership ban: with
-- `owning_squadron_id` no longer NOT NULL, an aggregate can legally carry a
-- null legacy column and a SpecialCommand-typed `owning_org_unit_id`. The
-- corresponding Java change drops the "Spezialkommando ownership of this
-- aggregate is not yet supported" rejection in
-- `OwnerScopeService.resolveSquadronForPickerOutput` — that change ships in
-- the same commit as this migration.
--
-- Tables touched: mission, operation, ship, inventory_item, refinery_order,
-- job_order (creating + requesting). promotion_topic is intentionally NOT
-- touched — promotion data stays Squadron-only per Plan §3.3 + the V98
-- guard_promotion_topic_owner_kind trigger.
-- =============================================================================

-- 1. NOT NULL on the new `owning_org_unit_id` columns. Safe because R4
-- populated them via the dual-write lifecycle hook on every persist / update.
ALTER TABLE mission         ALTER COLUMN owning_org_unit_id SET NOT NULL;
ALTER TABLE operation       ALTER COLUMN owning_org_unit_id SET NOT NULL;
ALTER TABLE ship            ALTER COLUMN owning_org_unit_id SET NOT NULL;
ALTER TABLE inventory_item  ALTER COLUMN owning_org_unit_id SET NOT NULL;
ALTER TABLE refinery_order  ALTER COLUMN owning_org_unit_id SET NOT NULL;
ALTER TABLE job_order       ALTER COLUMN creating_org_unit_id   SET NOT NULL;
ALTER TABLE job_order       ALTER COLUMN requesting_org_unit_id SET NOT NULL;

-- 2. DROP NOT NULL on the legacy columns. SK-owned rows must be able to carry
-- a null `owning_squadron_id` once the lifecycle hook decides not to mirror
-- (because `owning_org_unit_id` resolves to a SpecialCommand, not a Squadron).
ALTER TABLE mission         ALTER COLUMN owning_squadron_id DROP NOT NULL;
ALTER TABLE operation       ALTER COLUMN owning_squadron_id DROP NOT NULL;
ALTER TABLE ship            ALTER COLUMN owning_squadron_id DROP NOT NULL;
ALTER TABLE inventory_item  ALTER COLUMN owning_squadron_id DROP NOT NULL;
ALTER TABLE refinery_order  ALTER COLUMN owning_squadron_id DROP NOT NULL;
ALTER TABLE job_order       ALTER COLUMN creating_squadron_id   DROP NOT NULL;
ALTER TABLE job_order       ALTER COLUMN requesting_squadron_id DROP NOT NULL;

-- Rollback strategy: re-tighten NOT NULL on the legacy columns AND backfill
-- the now-null rows from `owning_org_unit_id` using a join through `org_unit`
-- filtered by `kind = 'SQUADRON'`. Practically irreversible in the presence
-- of any SK-owned aggregate, since those rows by design have no Squadron
-- mirror. Take a full DB backup before merging.
