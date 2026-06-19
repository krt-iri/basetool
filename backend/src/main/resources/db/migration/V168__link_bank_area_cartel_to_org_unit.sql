-- =====================================================================
-- V168 - Bank cascade (epic #692, REQ-ORG-019 / REQ-BANK-021): link the
--        AREA account to its Bereich and the CARTEL account to the OL.
-- =====================================================================
-- Why: with the real hierarchy (REQ-ORG-014) Bereiche and the
-- Organisationsleitung are first-class org_unit rows, so an AREA account can
-- reference its Bereich and the CARTEL account the OL through the SAME
-- org_unit_id FK the ORG_UNIT accounts already use, instead of the legacy
-- free-form area_name. This lets a Bereichsleitung/OL member's cascading
-- oversight scope (REQ-ORG-015, Phase 3) match its own AREA/CARTEL account and
-- drill down into subordinate accounts with no bank-side change -- the bank
-- stays org-unit-blind (REQ-BANK-008); all cascade logic lives in the single
-- OrgUnitBankAccessService seam.
--
-- This migration only widens the owner-reference CHECK so AREA may carry the
-- org_unit FK (a Bereich) and CARTEL may carry it (the OL). It creates no
-- account and backfills nothing: the org hierarchy is freshly introduced, so
-- there is no reliable name->Bereich mapping for any legacy area_name account.
-- Existing rows stay valid -- the legacy AREA-by-area_name form is still
-- accepted during the soak, and an unlinked CARTEL keeps working. New accounts
-- are created with the FK by BankAccountService.
--
-- Cardinality comes for free: the existing partial unique index
-- uq_bank_account_org_unit (org_unit_id WHERE org_unit_id IS NOT NULL) already
-- caps every org unit -- Staffel, SK, Bereich or OL -- at one account, so there
-- can be at most one AREA account per Bereich and one CARTEL per OL. The
-- uq_bank_account_singleton_cartel index keeps CARTEL a global singleton.
-- The CHECK cannot assert the referenced org_unit.kind (no cross-row lookup in
-- a column CHECK); BankAccountService validates kind = BEREICH for AREA and
-- kind = ORGANISATIONSLEITUNG for CARTEL at creation.
--
-- Idempotency: DROP CONSTRAINT IF EXISTS before ADD.
-- Rollback: restore the V150 chk_bank_account_owner_ref body. No data created.

ALTER TABLE bank_account DROP CONSTRAINT IF EXISTS chk_bank_account_owner_ref;
ALTER TABLE bank_account ADD CONSTRAINT chk_bank_account_owner_ref CHECK (
    -- ORG_UNIT: owned by exactly one Staffel/SK via the FK (unchanged).
    (type = 'ORG_UNIT' AND org_unit_id IS NOT NULL AND area_name IS NULL)
    -- AREA: linked to its Bereich via the FK (new, preferred) OR the legacy
    -- free-form name during the soak -- exactly one of the two is set.
    OR (type = 'AREA' AND (
            (org_unit_id IS NOT NULL AND area_name IS NULL)
            OR (org_unit_id IS NULL AND area_name IS NOT NULL)
        ))
    -- CARTEL: optionally linked to the OL via the FK; never carries an area name.
    OR (type = 'CARTEL' AND area_name IS NULL)
    -- CARTEL_BANK / SPECIAL: carry neither owner reference (unchanged).
    OR (type IN ('CARTEL_BANK', 'SPECIAL') AND org_unit_id IS NULL AND area_name IS NULL)
);

COMMENT ON COLUMN bank_account.org_unit_id IS
    'Owner org unit: a Staffel/SK for ORG_UNIT accounts, the Bereich for AREA accounts, the Organisationsleitung for CARTEL accounts (epic #692, REQ-ORG-019). NULL for legacy area_name AREA accounts and for CARTEL_BANK/SPECIAL.';
COMMENT ON COLUMN bank_account.area_name IS
    'Legacy free-form Bereich name for AREA accounts created before the Bereich FK (epic #692); NULL for FK-linked AREA accounts and every other type.';
