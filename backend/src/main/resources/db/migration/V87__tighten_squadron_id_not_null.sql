-- =====================================================================
-- V87 - Phase 7 part 2: tighten squadron-scope columns to NOT NULL
-- =====================================================================
-- Phase 7 of MULTI_SQUADRON_PLAN.md section 10: once a production deploy
-- of Phase 6 (V80-V86) has confirmed that every code path stamps the
-- new squadron columns on create, those columns can stop tolerating
-- NULL. The aggregate-scope columns (V82) and JobOrder's dual squadron
-- columns (V83) were added nullable so the backfill could run; the
-- promotion-topic scope column (V85) likewise. Phase 7 makes them NOT
-- NULL so Hibernate `ddl-auto=validate` and the application layer can
-- both assume the field is always present.
--
-- Defensive: the same backfill clauses from V82 / V83 / V85 are
-- repeated here as a safety net. They are idempotent (WHERE col IS
-- NULL) and a no-op on every well-formed prod row; the explicit UPDATE
-- catches any straggler that an early Phase-3 deploy might have left
-- behind before the entity stamp landed.
--
-- Ordering: this migration is numbered V87 — strictly between V86
-- (the per-squadron promotion-toggle from Phase 6) and V88 (the legacy
-- `job_order.squadron` VARCHAR stop-write from Phase 7 part 1, already
-- on main as of commit a00e5c8). Flyway's `out-of-order=false` default
-- expects migrations to apply in strict ascending order, so this file
-- MUST be in the source tree on the same release branch as V88 — both
-- migrations apply together on the next deploy.
--
-- Out of scope (intentionally NOT tightened):
--   * `app_user.squadron_id` — admins and guests must stay NULL there;
--     `SquadronScopeService.currentSquadron()` treats NULL squadron on
--     a JWT-sub-resolved user as "no concrete scope, span everything".
--   * `job_order.squadron` (legacy VARCHAR) — V88 already dropped that
--     column's NOT NULL constraint; V89 will drop the column entirely
--     in a future release iteration per the two-phase rule.

-- 5 strict-staffel aggregates (mission, operation, ship, inventory_item,
-- refinery_order) — owner stamp is mandatory.
UPDATE mission        SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE operation      SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE ship           SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE inventory_item SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE refinery_order SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;

ALTER TABLE mission        ALTER COLUMN owning_squadron_id SET NOT NULL;
ALTER TABLE operation      ALTER COLUMN owning_squadron_id SET NOT NULL;
ALTER TABLE ship           ALTER COLUMN owning_squadron_id SET NOT NULL;
ALTER TABLE inventory_item ALTER COLUMN owning_squadron_id SET NOT NULL;
ALTER TABLE refinery_order ALTER COLUMN owning_squadron_id SET NOT NULL;

-- Promotion-topic squadron scope (V85): mandatory after Phase 6 because
-- every read filter expects a non-null owning squadron to compute
-- visibility for non-admin callers.
UPDATE promotion_topic SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
ALTER TABLE promotion_topic ALTER COLUMN owning_squadron_id SET NOT NULL;

-- JobOrder dual squadron columns (V83):
--   * creating_squadron_id: who authored the order. Immutable post-create.
--   * requesting_squadron_id: who the order is being executed for.
-- Both must be present on every row after Phase 6's create / update
-- paths consistently stamp them; the entity layer rejects null on
-- input already, this just makes the DB invariant explicit.
UPDATE job_order SET creating_squadron_id   = '00000000-0000-0000-0000-000000000001' WHERE creating_squadron_id   IS NULL;
UPDATE job_order SET requesting_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE requesting_squadron_id IS NULL;
ALTER TABLE job_order ALTER COLUMN creating_squadron_id   SET NOT NULL;
ALTER TABLE job_order ALTER COLUMN requesting_squadron_id SET NOT NULL;
