-- =====================================================================
-- V163 - app_user: opt-in global blueprint sharing flag
-- =====================================================================
-- Why: a user may opt in (in their profile) to make their owned personal
-- blueprints count toward the leadership availability overview and the
-- item-order blueprint-coverage view for EVERY org unit, not only the ones
-- they are a member of (REQ-INV-018, ADR-0024). Default FALSE preserves the
-- existing org-unit-scoped behaviour for everyone who does not opt in; the
-- column is O(1) metadata (no backfill), so this ADD COLUMN is lock-cheap.

ALTER TABLE app_user
    ADD COLUMN share_blueprints_globally BOOLEAN NOT NULL DEFAULT FALSE;
