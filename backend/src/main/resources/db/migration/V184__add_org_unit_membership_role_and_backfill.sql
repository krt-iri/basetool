-- =====================================================================
-- V184 - Role model R1 (epic #800, REQ-ROLE-001): the unified membership
--        rank column. ADDITIVE + backfill; the 5 boolean leadership flags
--        stay and remain authoritative during the soak (dropped in the
--        Phase-5 cleanup).
-- =====================================================================
-- Why: docs/specs/role-model.md REQ-ROLE-001 + ADR-0042 replace the five
-- mutually-exclusive boolean leadership flags (is_lead, is_bereichsleiter,
-- is_bereichskoordinator, is_bereichsoperator, is_ol_member) with ONE
-- @Enumerated(STRING) rank column so squadron leadership ranks
-- (Staffelleiter / Kommandoleiter / stellv. Kommandoleiter / Ensign) can be
-- modelled too -- they have no boolean equivalent today.
--
-- This migration changes NO behaviour: the rank column is backfilled from
-- the existing booleans (which stay the authoritative read path) and the
-- service writes both in lockstep (dual-write) until Phase 2 switches the
-- authorisation layer onto the rank. is_logistician / is_mission_manager
-- are NOT folded in -- they remain orthogonal capability flags.
--
-- Kind-scoping: a CHECK confines each rank to the right org_unit kind,
-- mirroring chk_org_unit_membership_lead_only_on_special_command (V95) and
-- chk_org_unit_membership_bereich_flags_only_on_bereich (V164). It reads
-- the denormalised `kind` column, which the BEFORE-INSERT trigger
-- sync_org_unit_membership_kind populates before CHECKs are evaluated.
--
-- Idempotency: ADD COLUMN IF NOT EXISTS, DROP CONSTRAINT IF EXISTS before
-- ADD. Rollback: drop the constraint and the column.

-- ---------------------------------------------------------------------
-- 1. Add the rank column (defaults to MEMBER for every existing row)
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership
    ADD COLUMN IF NOT EXISTS role VARCHAR(40) NOT NULL DEFAULT 'MEMBER';

-- ---------------------------------------------------------------------
-- 2. Backfill the rank from the existing boolean flags
-- ---------------------------------------------------------------------
-- The five flags are mutually exclusive per their kind-scoped CHECKs
-- (is_lead only on SK, is_bereichs* only on BEREICH, is_ol_member only on
-- OL), so the CASE ladder is unambiguous. Every SQUADRON row maps to
-- MEMBER (squadron ranks did not exist before this epic) -- byte-identical
-- to today's behaviour.
UPDATE org_unit_membership
   SET role = CASE
       WHEN is_lead               THEN 'SK_LEAD'
       WHEN is_bereichsleiter      THEN 'BEREICHSLEITER'
       WHEN is_bereichskoordinator THEN 'BEREICHSKOORDINATOR'
       WHEN is_bereichsoperator    THEN 'BEREICHSOPERATOR'
       WHEN is_ol_member           THEN 'OL_MEMBER'
       ELSE 'MEMBER'
   END
 WHERE role = 'MEMBER';

-- ---------------------------------------------------------------------
-- 3. Kind-scope the rank (a single-row CHECK over role + the kind column)
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership DROP CONSTRAINT IF EXISTS chk_org_unit_membership_role_kind;
ALTER TABLE org_unit_membership ADD CONSTRAINT chk_org_unit_membership_role_kind
    CHECK (
        role = 'MEMBER'
        OR (role IN ('STAFFELLEITER', 'KOMMANDOLEITER', 'STELLV_KOMMANDOLEITER', 'ENSIGN')
            AND kind = 'SQUADRON')
        OR (role IN ('BEREICHSLEITER', 'BEREICHSKOORDINATOR', 'BEREICHSOPERATOR')
            AND kind = 'BEREICH')
        OR (role = 'OL_MEMBER' AND kind = 'ORGANISATIONSLEITUNG')
        OR (role = 'SK_LEAD' AND kind = 'SPECIAL_COMMAND')
    );
