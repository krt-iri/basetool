-- =====================================================================
-- V97 - Spezialkommando R1 step 1: create org_unit and copy squadron rows
-- =====================================================================
-- Why: SPEZIALKOMMANDO_PLAN.md introduces "Spezialkommando" (SK) as a
-- second tenant kind that coexists with Staffel under a shared abstraction
-- (D1: common parent table). The application's JPA layer will model this
-- as single-table inheritance over a new table `org_unit` with a
-- discriminator column `kind ∈ {SQUADRON, SPECIAL_COMMAND}`. Squadrons
-- become rows with `kind = SQUADRON`; SKs become rows with
-- `kind = SPECIAL_COMMAND`. Aggregates (mission, operation, ship,
-- inventory_item, refinery_order, job_order, promotion_topic) will
-- eventually reference `org_unit.id` via renamed FK columns - V99 adds
-- those columns side-by-side with the existing `*_squadron_id` ones so
-- code can land in stages (R2 dual-write) before the legacy columns are
-- dropped in R3.
--
-- This migration is step 1 of the staged rollout (PR-1 in
-- SPEZIALKOMMANDO_PLAN.md §10): create the new table and seed it with a
-- 1:1 copy of every existing squadron row. The legacy `squadron` table
-- is NOT dropped here - it stays as the read/write source of truth until
-- R2 stop-write lands. After V97 both tables exist with identical
-- contents (same UUIDs, including the IRIDIUM canonical
-- 00000000-0000-0000-0000-000000000001).
--
-- Idempotency: every statement uses IF NOT EXISTS / WHERE NOT EXISTS so
-- the migration is safe to re-run on a developer DB that may have
-- partial state from an aborted earlier attempt. Production runs through
-- Flyway only once per file hash, so the idempotency is purely a
-- developer-ergonomics safety net.
--
-- Promotion-feature CHECK: SKs must never carry the promotion subsystem
-- (per requirement). The constraint
--   (kind = 'SQUADRON' OR is_promotion_enabled = FALSE)
-- enforces this at the data layer so a careless UPDATE cannot enable
-- promotion on an SK row. Combined with the application-layer guard in
-- SpecialCommand's Java constructor (to land in R2), this is defense in
-- depth.
--
-- Rollback: drop the `org_unit` table. No application code references
-- it yet, so the rollback is non-destructive against running services.

-- ---------------------------------------------------------------------
-- 1. Create org_unit with the same column shape as squadron + new kind
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS org_unit (
    id                    UUID PRIMARY KEY,
    version               BIGINT,
    created_at            TIMESTAMP WITH TIME ZONE,
    updated_at            TIMESTAMP WITH TIME ZONE,
    kind                  VARCHAR(32)  NOT NULL,
    name                  VARCHAR(255) NOT NULL UNIQUE,
    shorthand             VARCHAR(255) NOT NULL UNIQUE,
    description           TEXT,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    is_promotion_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_org_unit_kind
        CHECK (kind IN ('SQUADRON', 'SPECIAL_COMMAND')),
    CONSTRAINT chk_org_unit_promotion_only_squadron
        CHECK (kind = 'SQUADRON' OR is_promotion_enabled = FALSE)
);

-- Index on the discriminator: the partial unique index on
-- org_unit_membership (V98) and several upcoming filter queries narrow
-- by `kind = 'SPECIAL_COMMAND'`. Cardinality is low but the index keeps
-- the planner from preferring a sequential scan on small workloads.
CREATE INDEX IF NOT EXISTS idx_org_unit_kind ON org_unit(kind);

-- ---------------------------------------------------------------------
-- 2. Copy every existing squadron row into org_unit (kind = SQUADRON)
-- ---------------------------------------------------------------------
-- Idempotent: only inserts rows that aren't already in org_unit. The
-- WHERE NOT EXISTS / NOT IN form is preferred over ON CONFLICT here
-- because the source and target share the same PK shape and a re-run
-- after V105 (legacy squadron dropped) must remain a no-op even when
-- some org_unit rows are SKs the squadron table doesn't know about.
INSERT INTO org_unit (id, version, created_at, updated_at, kind, name, shorthand, description, active, is_promotion_enabled)
SELECT s.id,
       COALESCE(s.version, 0),
       COALESCE(s.created_at, NOW()),
       COALESCE(s.updated_at, NOW()),
       'SQUADRON',
       s.name,
       s.shorthand,
       s.description,
       COALESCE(s.active, TRUE),
       COALESCE(s.is_promotion_enabled, TRUE)
  FROM squadron s
 WHERE NOT EXISTS (
       SELECT 1 FROM org_unit o WHERE o.id = s.id
   );
