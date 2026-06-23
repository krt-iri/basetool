-- =====================================================================
-- V186 - Role model R4 (epic #800, REQ-ROLE-006): link the org-chart
--        Kommando node (COMMAND_LEAD) to its kommando_group, so the
--        account-linked chart seats become a faithful mirror of the
--        functional ranks. ADDITIVE.
-- =====================================================================
-- Why: REQ-ROLE-006 + ADR-0042 make the functional rank on
-- org_unit_membership the single source of truth and the org chart its
-- descriptive mirror. The flat seats (Bereichsleiter / -koordinator /
-- -operator, OL member, SK-Leiter, Staffelleiter) map 1:1 onto a chart
-- position keyed by org_unit_id, but the in-Kommando ranks (Kommandoleiter /
-- stellv. Kommandoleiter / Ensign) live under a COMMAND_LEAD node the chart
-- modelled independently. This migration adds a nullable kommando_group_id
-- FK on org_chart_position so a mirrored COMMAND_LEAD is tied 1:1 to its
-- kommando_group, letting OrgChartService.mirror* fill / vacate the
-- Kommandoleiter and hang the stellv. / Ensigns off the right node.
--
-- Additive: the column defaults NULL; existing (admin-authored) COMMAND_LEAD
-- rows keep a NULL link and stay editable through the chart as before. New
-- kommando_group-backed Kommandos created from the Leitung UI carry the link.
--
-- Constraints:
--   * chk_org_chart_kommando_group_type - a group link is only valid on a
--     COMMAND_LEAD row (every other rank keeps it NULL), mirroring the
--     existing chk_org_chart_name "name only on a Kommando" rule.
--   * uq_org_chart_one_command_per_group - at most one COMMAND_LEAD mirrors a
--     given kommando_group (the mirror's find-or-create key).
--   * FK ON DELETE CASCADE - deleting a kommando_group removes its mirrored
--     COMMAND_LEAD (and, via the parent_id cascade, its stellv. / Ensigns);
--     KommandoGroupService still deletes the node explicitly and guards that
--     no members remain, so this is a backstop.
--
-- Idempotency: ADD COLUMN / CONSTRAINT / INDEX IF NOT EXISTS guards.
-- Rollback: drop the index, the constraint, the FK and the column.

ALTER TABLE org_chart_position
    ADD COLUMN IF NOT EXISTS kommando_group_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
         WHERE constraint_name = 'fk_org_chart_position_kommando_group'
           AND table_name = 'org_chart_position'
    ) THEN
        ALTER TABLE org_chart_position
            ADD CONSTRAINT fk_org_chart_position_kommando_group
            FOREIGN KEY (kommando_group_id) REFERENCES kommando_group (id) ON DELETE CASCADE;
    END IF;
END$$;

-- A group link only makes sense on a Kommando node (COMMAND_LEAD).
ALTER TABLE org_chart_position DROP CONSTRAINT IF EXISTS chk_org_chart_kommando_group_type;
ALTER TABLE org_chart_position
    ADD CONSTRAINT chk_org_chart_kommando_group_type
        CHECK (kommando_group_id IS NULL OR position_type = 'COMMAND_LEAD');

-- At most one COMMAND_LEAD mirrors a given kommando_group (the mirror's key).
DROP INDEX IF EXISTS uq_org_chart_one_command_per_group;
CREATE UNIQUE INDEX uq_org_chart_one_command_per_group
    ON org_chart_position (kommando_group_id) WHERE kommando_group_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_org_chart_kommando_group
    ON org_chart_position (kommando_group_id);
