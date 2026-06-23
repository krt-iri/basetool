-- =====================================================================
-- V187 - Role model R5 (epic #800, REQ-ROLE-001): drop the five legacy
--        boolean leadership flags now that `role` is the sole source of
--        truth. Destructive cleanup; runs after the Phase 1-4 soak.
-- =====================================================================
-- Why: V184 added the unified @Enumerated `role` column and backfilled it
-- from the five mutually-exclusive booleans (is_lead, is_bereichsleiter,
-- is_bereichskoordinator, is_bereichsoperator, is_ol_member). Phase 2
-- switched the whole authorisation layer (CustomJwtGrantedAuthoritiesConverter
-- / OrgUnitCascadeService / OwnerScopeService) onto `role`, and Phases 3-4
-- write only `role`. Nothing reads the booleans any more (the SK-Lead /
-- Bereichsleitung wire shapes derive their booleans from `role` at the
-- controller / mapper boundary), so the columns are now dead weight.
--
-- This migration:
--   1. rewrites the V165 enforce_leader_excludes_squadron() trigger
--      function onto `role` (squadron ranks EXEMPT — a Staffelleiter /
--      Kommandoleiter / stellv. / Ensign IS a Staffel member; the silo set
--      is SK_LEAD + the three Bereich ranks + OL_MEMBER), so the cross-row
--      REQ-ORG-017 invariant survives the column drop;
--   2. drops the three boolean-referencing CHECK constraints
--      (chk_org_unit_membership_lead_only_on_special_command from V98,
--      chk_org_unit_membership_bereich_flags_only_on_bereich and
--      chk_org_unit_membership_ol_flag_only_on_ol from V164) — the V184
--      chk_org_unit_membership_role_kind CHECK already kind-scopes `role`;
--   3. drops the five boolean columns.
--
-- The function is replaced BEFORE the columns are dropped so the trigger
-- never references a dropped column. The other org_unit_membership triggers
-- (sync_org_unit_membership_kind, the <=2 Staffel counting triggers) and the
-- one_squadron partial unique index are untouched — none reference the flags.
--
-- Idempotency: CREATE OR REPLACE FUNCTION, DROP CONSTRAINT IF EXISTS,
-- DROP COLUMN IF EXISTS. Rollback: re-add the columns + CHECKs and restore
-- the boolean-based function body (see V098/V164/V165).

-- ---------------------------------------------------------------------
-- 1. Rewrite the cross-row leader-excludes-Staffel trigger onto `role`
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION enforce_leader_excludes_squadron()
RETURNS TRIGGER AS $$
BEGIN
    -- Silo-leadership ranks must hold NO Staffel membership. Squadron ranks
    -- (STAFFELLEITER / KOMMANDOLEITER / STELLV_KOMMANDOLEITER / ENSIGN) and a
    -- rank-less MEMBER seat are EXEMPT from this branch — they ARE Staffel
    -- members (epic #800, REQ-ROLE-001 / REQ-ORG-017).
    IF NEW.role IN ('SK_LEAD', 'BEREICHSLEITER', 'BEREICHSKOORDINATOR',
                    'BEREICHSOPERATOR', 'OL_MEMBER') THEN
        IF EXISTS (
            SELECT 1 FROM org_unit_membership
             WHERE user_id = NEW.user_id
               AND kind = 'SQUADRON'
        ) THEN
            RAISE EXCEPTION
                'user % holds a leadership role (SK-Lead/Bereichsleitung/OL) and may not belong to a Staffel',
                NEW.user_id;
        END IF;
    ELSIF NEW.kind = 'SQUADRON' THEN
        -- The reverse direction: a Staffel membership (any squadron rank or a
        -- plain member) forbids a silo-leadership rank on a DIFFERENT unit.
        IF EXISTS (
            SELECT 1 FROM org_unit_membership
             WHERE user_id = NEW.user_id
               AND org_unit_id <> NEW.org_unit_id
               AND role IN ('SK_LEAD', 'BEREICHSLEITER', 'BEREICHSKOORDINATOR',
                            'BEREICHSOPERATOR', 'OL_MEMBER')
        ) THEN
            RAISE EXCEPTION
                'user % belongs to a Staffel and may not hold a leadership role (SK-Lead/Bereichsleitung/OL)',
                NEW.user_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 2. Drop the three boolean-referencing CHECK constraints
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership
    DROP CONSTRAINT IF EXISTS chk_org_unit_membership_lead_only_on_special_command;
ALTER TABLE org_unit_membership
    DROP CONSTRAINT IF EXISTS chk_org_unit_membership_bereich_flags_only_on_bereich;
ALTER TABLE org_unit_membership
    DROP CONSTRAINT IF EXISTS chk_org_unit_membership_ol_flag_only_on_ol;

-- ---------------------------------------------------------------------
-- 3. Drop the five boolean leadership columns
-- ---------------------------------------------------------------------
ALTER TABLE org_unit_membership DROP COLUMN IF EXISTS is_lead;
ALTER TABLE org_unit_membership DROP COLUMN IF EXISTS is_bereichsleiter;
ALTER TABLE org_unit_membership DROP COLUMN IF EXISTS is_bereichskoordinator;
ALTER TABLE org_unit_membership DROP COLUMN IF EXISTS is_bereichsoperator;
ALTER TABLE org_unit_membership DROP COLUMN IF EXISTS is_ol_member;
