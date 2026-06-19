-- =====================================================================
-- V164 - Org hierarchy R1 (epic #692): Bereich + Organisationsleitung
--        kinds, parent_org_unit_id, membership leadership flags, and the
--        relaxed "up to two Staffeln" cap. PURELY ADDITIVE.
-- =====================================================================
-- Why: docs/specs/org-unit-tenancy.md REQ-ORG-014 / REQ-ORG-017 + ADR-0025
-- introduce two org-unit kinds ABOVE Staffel/SK -- BEREICH (area, e.g.
-- Profit, Sub-Radar, Raumueberlegenheit) and ORGANISATIONSLEITUNG (OL) --
-- and a fixed three-level hierarchy OL > Bereich > (Staffel | SK) via a
-- nullable self-referential parent_org_unit_id. Leadership of a Bereich /
-- the OL is modelled as new per-membership flags on org_unit_membership,
-- mirroring is_lead. A member may now belong to up to TWO Staffeln.
--
-- This migration changes NO behaviour on its own: parent_org_unit_id is
-- NULL for every existing row, no leadership flag is set, and no BEREICH /
-- OL row is seeded (admins create them in a later phase). The scope
-- resolver degrades to today's flat behaviour while every parent is NULL.
--
-- Cross-row rules use triggers (a Postgres CHECK cannot reference another
-- row): the parent's kind must match the child's level, and a user may
-- hold at most two Staffel memberships. Single-row rules stay CHECKs.
-- Service-layer guards (REQ-ORG-017) add defence in depth in a later phase.
--
-- Idempotency: DROP ... IF EXISTS before ADD, CREATE OR REPLACE FUNCTION,
-- ADD COLUMN IF NOT EXISTS, CREATE INDEX IF NOT EXISTS, guarded FK add.
-- Rollback: drop the new columns / constraints / triggers; no data created.

-- ---------------------------------------------------------------------
-- 1. Allow the two new kinds on org_unit and org_unit_membership
-- ---------------------------------------------------------------------
ALTER TABLE org_unit DROP CONSTRAINT IF EXISTS chk_org_unit_kind;
ALTER TABLE org_unit ADD CONSTRAINT chk_org_unit_kind
    CHECK (kind IN ('SQUADRON', 'SPECIAL_COMMAND', 'BEREICH', 'ORGANISATIONSLEITUNG'));

-- chk_org_unit_promotion_only_squadron is intentionally left unchanged: it
-- already forces is_promotion_enabled = FALSE for every non-SQUADRON kind,
-- so BEREICH and ORGANISATIONSLEITUNG inherit the same promotion ban as SK.

ALTER TABLE org_unit_membership DROP CONSTRAINT IF EXISTS chk_org_unit_membership_kind;
ALTER TABLE org_unit_membership ADD CONSTRAINT chk_org_unit_membership_kind
    CHECK (kind IN ('SQUADRON', 'SPECIAL_COMMAND', 'BEREICH', 'ORGANISATIONSLEITUNG'));

-- ---------------------------------------------------------------------
-- 2. Self-referential parent_org_unit_id (fixed three-level hierarchy)
-- ---------------------------------------------------------------------
ALTER TABLE org_unit ADD COLUMN IF NOT EXISTS parent_org_unit_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_org_unit_parent' AND table_name = 'org_unit'
    ) THEN
        ALTER TABLE org_unit
            ADD CONSTRAINT fk_org_unit_parent
            FOREIGN KEY (parent_org_unit_id) REFERENCES org_unit(id);
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_org_unit_parent ON org_unit(parent_org_unit_id);

-- Single-row rule: an ORGANISATIONSLEITUNG row has no parent.
ALTER TABLE org_unit DROP CONSTRAINT IF EXISTS chk_org_unit_ol_has_no_parent;
ALTER TABLE org_unit ADD CONSTRAINT chk_org_unit_ol_has_no_parent
    CHECK (kind <> 'ORGANISATIONSLEITUNG' OR parent_org_unit_id IS NULL);

-- Cross-row rule (trigger): the parent kind must match the child's level.
--   SQUADRON / SPECIAL_COMMAND -> parent must be BEREICH
--   BEREICH                    -> parent must be ORGANISATIONSLEITUNG
--   ORGANISATIONSLEITUNG       -> no parent (also pinned by the CHECK above)
-- A NULL parent always passes (additive soak: the hierarchy is wired up
-- gradually and the scope cascade treats a NULL parent as "no ancestor").
CREATE OR REPLACE FUNCTION validate_org_unit_parent()
RETURNS TRIGGER AS $$
DECLARE
    parent_kind VARCHAR(32);
BEGIN
    IF NEW.parent_org_unit_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.parent_org_unit_id = NEW.id THEN
        RAISE EXCEPTION 'org_unit % cannot be its own parent', NEW.id;
    END IF;
    SELECT kind INTO parent_kind FROM org_unit WHERE id = NEW.parent_org_unit_id;
    IF parent_kind IS NULL THEN
        RAISE EXCEPTION 'parent org_unit % does not exist', NEW.parent_org_unit_id;
    END IF;
    IF NEW.kind IN ('SQUADRON', 'SPECIAL_COMMAND') AND parent_kind <> 'BEREICH' THEN
        RAISE EXCEPTION 'a % must have a BEREICH parent, not %', NEW.kind, parent_kind;
    ELSIF NEW.kind = 'BEREICH' AND parent_kind <> 'ORGANISATIONSLEITUNG' THEN
        RAISE EXCEPTION 'a BEREICH must have an ORGANISATIONSLEITUNG parent, not %', parent_kind;
    ELSIF NEW.kind = 'ORGANISATIONSLEITUNG' THEN
        RAISE EXCEPTION 'an ORGANISATIONSLEITUNG must not have a parent';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_org_unit_parent_ins ON org_unit;
CREATE TRIGGER trg_org_unit_parent_ins
BEFORE INSERT ON org_unit
FOR EACH ROW EXECUTE FUNCTION validate_org_unit_parent();

DROP TRIGGER IF EXISTS trg_org_unit_parent_upd ON org_unit;
CREATE TRIGGER trg_org_unit_parent_upd
BEFORE UPDATE OF parent_org_unit_id, kind ON org_unit
FOR EACH ROW EXECUTE FUNCTION validate_org_unit_parent();

-- ---------------------------------------------------------------------
-- 3. Leadership flags on org_unit_membership (mirror is_lead)
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership ADD COLUMN IF NOT EXISTS is_bereichsleiter      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE org_unit_membership ADD COLUMN IF NOT EXISTS is_bereichskoordinator BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE org_unit_membership ADD COLUMN IF NOT EXISTS is_bereichsoperator    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE org_unit_membership ADD COLUMN IF NOT EXISTS is_ol_member           BOOLEAN NOT NULL DEFAULT FALSE;

-- Bereich-leadership flags are valid only on a BEREICH membership.
ALTER TABLE org_unit_membership DROP CONSTRAINT IF EXISTS chk_org_unit_membership_bereich_flags_only_on_bereich;
ALTER TABLE org_unit_membership ADD CONSTRAINT chk_org_unit_membership_bereich_flags_only_on_bereich
    CHECK (
        (is_bereichsleiter = FALSE AND is_bereichskoordinator = FALSE AND is_bereichsoperator = FALSE)
        OR kind = 'BEREICH'
    );

-- The OL-member flag is valid only on an ORGANISATIONSLEITUNG membership.
ALTER TABLE org_unit_membership DROP CONSTRAINT IF EXISTS chk_org_unit_membership_ol_flag_only_on_ol;
ALTER TABLE org_unit_membership ADD CONSTRAINT chk_org_unit_membership_ol_flag_only_on_ol
    CHECK (is_ol_member = FALSE OR kind = 'ORGANISATIONSLEITUNG');

-- ---------------------------------------------------------------------
-- 4. Relax "at most one Staffel per user" to "at most two"
-- ---------------------------------------------------------------------
-- A partial UNIQUE index can enforce "<=1" but not "<=2"; replace it with
-- a counting trigger. Existing data (<=1 Staffel per user) stays valid; the
-- service layer adds a matching guard (REQ-ORG-017) in a later phase.
--
-- Coverage parity: the dropped unique index enforced the cap on every write
-- (INSERT and UPDATE), so the replacement runs on BOTH operations too. Each
-- max-two trigger fires AFTER sync_org_unit_membership_kind on the same
-- operation (alphabetical order: "..._kind_ins" < "..._max_two_squadron_ins"
-- and "..._kind_upd" < "..._max_two_squadron_upd"), so NEW.kind is already
-- resolved when the count runs.
--
-- INSERT vs UPDATE counting: the function counts the user's OTHER Staffel
-- memberships and rejects when there are already >= 2 (the row being written
-- would be the third). The row being written must be excluded from that
-- count. On INSERT no such row exists yet, so excluding NEW's (not-yet-
-- present) identity is a harmless no-op. On UPDATE the row still holds its
-- OLD identity in the table, so it MUST be excluded by OLD -- otherwise a
-- valid re-point (e.g. moving a membership from Staffel A to B while the user
-- is also in C: A,C -> B,C, still two) would miscount the about-to-be-
-- replaced A row as a third Staffel and be wrongly rejected.
DROP INDEX IF EXISTS uq_org_unit_membership_one_squadron;

CREATE OR REPLACE FUNCTION enforce_max_two_squadron_memberships()
RETURNS TRIGGER AS $$
DECLARE
    other_squadron_count INTEGER;
    self_user_id     UUID;
    self_org_unit_id UUID;
BEGIN
    IF NEW.kind <> 'SQUADRON' THEN
        RETURN NEW;
    END IF;
    -- Identity of the row being written, so it is never counted against itself.
    IF TG_OP = 'UPDATE' THEN
        self_user_id     := OLD.user_id;
        self_org_unit_id := OLD.org_unit_id;
    ELSE
        self_user_id     := NEW.user_id;
        self_org_unit_id := NEW.org_unit_id;
    END IF;
    SELECT COUNT(*) INTO other_squadron_count
      FROM org_unit_membership
     WHERE user_id = NEW.user_id
       AND kind = 'SQUADRON'
       AND NOT (user_id = self_user_id AND org_unit_id = self_org_unit_id);
    IF other_squadron_count >= 2 THEN
        RAISE EXCEPTION 'user % may belong to at most two Staffeln', NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_org_unit_membership_max_two_squadron_ins ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_max_two_squadron_ins
BEFORE INSERT ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION enforce_max_two_squadron_memberships();

DROP TRIGGER IF EXISTS trg_org_unit_membership_max_two_squadron_upd ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_max_two_squadron_upd
BEFORE UPDATE OF user_id, org_unit_id ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION enforce_max_two_squadron_memberships();
