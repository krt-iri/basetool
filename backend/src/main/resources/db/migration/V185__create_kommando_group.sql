-- =====================================================================
-- V185 - Role model R1 (epic #800, REQ-ROLE-003): the Kommandogruppe
--        entity + the membership -> group link. ADDITIVE.
-- =====================================================================
-- Why: docs/specs/role-model.md REQ-ROLE-003 + ADR-0042 make a
-- Kommandogruppe a first-class named sub-structure of a Staffel that a
-- Kommandoleiter leads, a stellv. Kommandoleiter deputises, and Ensigns
-- may be assigned to. A squadron has at most four groups.
--
-- This migration is additive: it creates an empty kommando_group table and
-- a nullable kommando_group_id on org_unit_membership (all rows NULL), so
-- it changes no behaviour. Groups + member->group links are written from
-- Phase 3 (the assignment API).
--
-- Cross-row / cross-table rules use triggers (a Postgres CHECK cannot
-- reference another row/table): the parent must be a SQUADRON, and a
-- squadron may hold at most four groups. The single-row "which ranks may
-- carry a group" rule stays a CHECK. Both counting triggers reuse the
-- OLD/NEW self-exclusion logic proven in V164's
-- enforce_max_two_squadron_memberships.
--
-- Idempotency: CREATE TABLE/INDEX/COLUMN IF NOT EXISTS, CREATE OR REPLACE
-- FUNCTION, DROP TRIGGER/CONSTRAINT IF EXISTS. Rollback: drop the column,
-- the triggers/functions and the table.

-- ---------------------------------------------------------------------
-- 1. kommando_group table (AbstractEntity: version + audit timestamps)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kommando_group (
    id                   UUID PRIMARY KEY,
    squadron_org_unit_id UUID                     NOT NULL REFERENCES org_unit (id) ON DELETE CASCADE,
    name                 VARCHAR(120)             NOT NULL,
    sort_index           INTEGER                  NOT NULL DEFAULT 0,
    version              BIGINT                   NOT NULL DEFAULT 0,
    created_at           TIMESTAMP WITH TIME ZONE,
    updated_at           TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_kommando_group_squadron
    ON kommando_group (squadron_org_unit_id);

COMMENT ON TABLE kommando_group IS
    'A named command group within a Staffel (REQ-ROLE-003). At most four per squadron; descriptive structure only, grants no rights.';

-- ---------------------------------------------------------------------
-- 2. Cross-table rule (trigger): a group's parent must be a SQUADRON
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION validate_kommando_group_squadron()
RETURNS TRIGGER AS $$
DECLARE
    parent_kind VARCHAR(32);
BEGIN
    SELECT kind INTO parent_kind FROM org_unit WHERE id = NEW.squadron_org_unit_id;
    IF parent_kind IS NULL THEN
        RAISE EXCEPTION 'org_unit % referenced by kommando_group does not exist', NEW.squadron_org_unit_id;
    END IF;
    IF parent_kind <> 'SQUADRON' THEN
        RAISE EXCEPTION 'a kommando_group must belong to a SQUADRON, not %', parent_kind;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kommando_group_squadron_ins ON kommando_group;
CREATE TRIGGER trg_kommando_group_squadron_ins
BEFORE INSERT ON kommando_group
FOR EACH ROW EXECUTE FUNCTION validate_kommando_group_squadron();

DROP TRIGGER IF EXISTS trg_kommando_group_squadron_upd ON kommando_group;
CREATE TRIGGER trg_kommando_group_squadron_upd
BEFORE UPDATE OF squadron_org_unit_id ON kommando_group
FOR EACH ROW EXECUTE FUNCTION validate_kommando_group_squadron();

-- ---------------------------------------------------------------------
-- 3. Cross-row rule (trigger): at most four groups per squadron
-- ---------------------------------------------------------------------
-- Mirrors enforce_max_two_squadron_memberships (V164): count the OTHER
-- groups of the same squadron and reject when there are already >= 4 (the
-- row being written would be the fifth). On UPDATE the row keeps its OLD
-- identity in the table, so it must be excluded by OLD.id; on INSERT the
-- (not-yet-present) NEW.id exclusion is a harmless no-op.
CREATE OR REPLACE FUNCTION enforce_max_four_kommando_groups_per_squadron()
RETURNS TRIGGER AS $$
DECLARE
    other_group_count INTEGER;
    self_id           UUID;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        self_id := OLD.id;
    ELSE
        self_id := NEW.id;
    END IF;
    SELECT COUNT(*) INTO other_group_count
      FROM kommando_group
     WHERE squadron_org_unit_id = NEW.squadron_org_unit_id
       AND id <> self_id;
    IF other_group_count >= 4 THEN
        RAISE EXCEPTION 'squadron % may have at most four Kommandogruppen', NEW.squadron_org_unit_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_kommando_group_max_four_ins ON kommando_group;
CREATE TRIGGER trg_kommando_group_max_four_ins
BEFORE INSERT ON kommando_group
FOR EACH ROW EXECUTE FUNCTION enforce_max_four_kommando_groups_per_squadron();

DROP TRIGGER IF EXISTS trg_kommando_group_max_four_upd ON kommando_group;
CREATE TRIGGER trg_kommando_group_max_four_upd
BEFORE UPDATE OF squadron_org_unit_id ON kommando_group
FOR EACH ROW EXECUTE FUNCTION enforce_max_four_kommando_groups_per_squadron();

-- ---------------------------------------------------------------------
-- 4. Member -> group link on org_unit_membership (nullable; all rows NULL)
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership
    ADD COLUMN IF NOT EXISTS kommando_group_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_org_unit_membership_kommando_group'
           AND table_name = 'org_unit_membership'
    ) THEN
        ALTER TABLE org_unit_membership
            ADD CONSTRAINT fk_org_unit_membership_kommando_group
            FOREIGN KEY (kommando_group_id) REFERENCES kommando_group (id);
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_org_unit_membership_kommando_group
    ON org_unit_membership (kommando_group_id);

-- Single-row rule: a group link is only valid for the in-group squadron
-- ranks. Kommandoleiter / stellv. Kommandoleiter MUST reference a group;
-- Ensign MAY (null = "allgemein der Staffelleitung"); every other rank
-- (incl. MEMBER) must have a NULL group.
ALTER TABLE org_unit_membership DROP CONSTRAINT IF EXISTS chk_org_unit_membership_kommando_group_role;
ALTER TABLE org_unit_membership ADD CONSTRAINT chk_org_unit_membership_kommando_group_role
    CHECK (
        (kommando_group_id IS NULL
            AND role NOT IN ('KOMMANDOLEITER', 'STELLV_KOMMANDOLEITER'))
        OR (kommando_group_id IS NOT NULL
            AND role IN ('KOMMANDOLEITER', 'STELLV_KOMMANDOLEITER', 'ENSIGN'))
    );
