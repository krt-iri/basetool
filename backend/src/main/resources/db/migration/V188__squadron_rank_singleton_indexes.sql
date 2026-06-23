-- =====================================================================
-- V188 - Role model R5+ (epic #800, REQ-ROLE-003): DB backstops for the
--        squadron-rank singleton caps. Defence in depth behind the
--        service-layer assertSquadronRankCardinality check.
-- =====================================================================
-- Why: the unified `role` column carries the squadron leadership ranks,
-- whose singleton caps -- at most one Staffelleiter per Staffel, at most
-- one Kommandoleiter and one stellv. Kommandoleiter per Kommandogruppe --
-- were enforced only in the application layer
-- (OrgUnitMembershipService#assertSquadronRankCardinality). Two concurrent
-- assignSquadronRank calls could both pass the in-memory roster check and
-- commit two STAFFELLEITER rows. These partial unique indexes make the
-- singleton caps airtight at the DB layer, mirroring the org chart's
-- uq_org_chart_one_squadron_lead / uq_org_chart_one_deputy_per_command
-- (V136); a concurrent double-assign now fails cleanly on the constraint.
--
-- The <=4-Ensigns-per-Staffel cap is intentionally NOT added here: it is a
-- count, not a uniqueness rule, and stays service-layer-only exactly like
-- the org chart's own <=4 ENSIGN cap -- a concurrent overshoot by one is
-- harmless and an admin simply removes the surplus.
--
-- Safe on existing data: the squadron ranks are net-new this epic (every
-- pre-existing org_unit_membership row was backfilled to MEMBER in V184 and
-- the only STAFFELLEITER / KOMMANDOLEITER / STELLV_KOMMANDOLEITER rows are
-- created through the cap-checked appointment flow), so no duplicates exist
-- and the index build cannot fail on legacy rows. The KOMMANDOLEITER /
-- STELLV indexes key on kommando_group_id, which the V185
-- chk_org_unit_membership_kommando_group_role CHECK already forces non-null
-- for those ranks.
--
-- Idempotency: CREATE UNIQUE INDEX IF NOT EXISTS. Rollback: drop the three
-- indexes.

-- At most one Staffelleiter per Staffel.
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_unit_membership_one_staffelleiter
    ON org_unit_membership (org_unit_id)
    WHERE role = 'STAFFELLEITER';

-- At most one Kommandoleiter per Kommandogruppe.
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_unit_membership_one_kommandoleiter_per_group
    ON org_unit_membership (kommando_group_id)
    WHERE role = 'KOMMANDOLEITER';

-- At most one stellv. Kommandoleiter per Kommandogruppe.
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_unit_membership_one_stellv_per_group
    ON org_unit_membership (kommando_group_id)
    WHERE role = 'STELLV_KOMMANDOLEITER';
