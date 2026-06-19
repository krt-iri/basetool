-- =====================================================================
-- V167 - Org-chart R3 (epic #692, REQ-ORG-018): Bereich + OL chart tiers.
-- =====================================================================
-- Why: REQ-ORG-018 widens the org chart from the single legacy "Profit-
-- Bereich" (OrgChartScope.AREA, a global singleton with org_unit_id NULL)
-- to the real hierarchy OL -> each Bereich -> its Staffeln/SKs. Two new
-- scopes carry positions bound to a concrete org unit by org_unit_id:
--   * BEREICH ranks (BEREICHSLEITER / -KOORDINATOR / -OPERATOR) reference
--     the Bereich org_unit;
--   * OL rank (OL_MEMBER) references the Organisationsleitung org_unit.
-- The legacy AREA ranks (org_unit_id IS NULL) are left untouched, so the
-- chart degrades to today's rendering until Bereiche/OL are created.
--
-- This migration is purely additive at the data level: it does not create
-- any position. It (1) widens chk_org_chart_scope so the new ranks are
-- accepted with a non-null org_unit_id, and (2) adds a partial unique index
-- enforcing "at most one Bereichsleiter PER Bereich" (per org_unit_id) --
-- the per-Bereich analogue of the global uq_org_chart_one_area_lead. The
-- existing uq_org_chart_user_per_unit (org_unit_id, user_id) already caps a
-- user to one position per Bereich/OL, so no new user-uniqueness index is
-- needed. The service layer (OrgChartService) adds matching guards.
--
-- Idempotency: DROP CONSTRAINT/INDEX IF EXISTS before ADD/CREATE.
-- Rollback: restore the V136 chk_org_chart_scope body and drop the new
-- index; no data created.

ALTER TABLE org_chart_position DROP CONSTRAINT IF EXISTS chk_org_chart_scope;
ALTER TABLE org_chart_position ADD CONSTRAINT chk_org_chart_scope CHECK (
    (position_type IN ('AREA_LEAD', 'AREA_COORDINATOR', 'AREA_OPERATOR', 'AREA_COMMANDER')
        AND org_unit_id IS NULL)
    OR
    (position_type IN ('SQUADRON_LEAD', 'COMMAND_LEAD', 'DEPUTY_COMMAND_LEAD', 'ENSIGN',
                       'SK_COMMANDER',
                       'BEREICHSLEITER', 'BEREICHSKOORDINATOR', 'BEREICHSOPERATOR', 'OL_MEMBER')
        AND org_unit_id IS NOT NULL)
);

-- At most one Bereichsleiter per Bereich (scoped to org_unit_id), mirroring
-- uq_org_chart_one_squadron_lead. The legacy uq_org_chart_one_area_lead
-- (global singleton on AREA_LEAD) is untouched.
DROP INDEX IF EXISTS uq_org_chart_one_bereichsleiter_per_bereich;
CREATE UNIQUE INDEX uq_org_chart_one_bereichsleiter_per_bereich
    ON org_chart_position (org_unit_id) WHERE position_type = 'BEREICHSLEITER';
