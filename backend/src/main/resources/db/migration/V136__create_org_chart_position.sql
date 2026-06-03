-- Org chart of the Profit-Bereich (#385 follow-up): the org_chart_position aggregate.
--
-- One row = one filled functional-rank position. The chart is purely descriptive — it never
-- grants any application permission (authorization stays with the role/flag model). Three scopes
-- live in one table, distinguished by org_unit_id:
--   * Bereichsleitung (area leadership)  -> org_unit_id IS NULL
--   * Staffel (Squadron) / Spezialkommando (SK) -> org_unit_id REFERENCES org_unit(id)
--
-- The intra-Staffel tree (Stv. Kommandoleiter under a Kommandoleiter; Ensign under the
-- Staffelleiter or a Kommandoleiter) is modelled with the self-referencing parent_id. Cardinality
-- limits that SQL cannot express cleanly (<=4 Kommandoleiter / <=4 Ensign per Staffel, 1-2
-- SK-Leiter per SK) are enforced in OrgChartService; the structural "at most one" invariants are
-- backstopped here with partial unique indexes.
CREATE TABLE org_chart_position (
    id            UUID                     PRIMARY KEY,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    position_type VARCHAR(40)              NOT NULL,
    org_unit_id   UUID                     REFERENCES org_unit(id) ON DELETE CASCADE,
    user_id       UUID                     NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    parent_id     UUID                     REFERENCES org_chart_position(id) ON DELETE CASCADE,
    sort_index    INTEGER                  NOT NULL DEFAULT 0,
    -- Area-leadership ranks are scope-free (no OrgUnit); every other rank lives inside an OrgUnit.
    CONSTRAINT chk_org_chart_scope CHECK (
        (position_type IN ('AREA_LEAD', 'AREA_COORDINATOR', 'AREA_OPERATOR', 'AREA_COMMANDER')
            AND org_unit_id IS NULL)
        OR
        (position_type IN ('SQUADRON_LEAD', 'COMMAND_LEAD', 'DEPUTY_COMMAND_LEAD', 'ENSIGN',
                           'SK_COMMANDER')
            AND org_unit_id IS NOT NULL)
    ),
    -- Only a Stv. Kommandoleiter or an Ensign may hang off a parent position; all others are roots.
    CONSTRAINT chk_org_chart_parent CHECK (
        position_type IN ('DEPUTY_COMMAND_LEAD', 'ENSIGN') OR parent_id IS NULL
    )
);

-- FK lookup indexes (chart assembly reads by org_unit_id; cascade + child reads use parent_id).
CREATE INDEX idx_org_chart_org_unit ON org_chart_position (org_unit_id);
CREATE INDEX idx_org_chart_user ON org_chart_position (user_id);
CREATE INDEX idx_org_chart_parent ON org_chart_position (parent_id);

-- Exactly one Bereichsleiter overall.
CREATE UNIQUE INDEX uq_org_chart_one_area_lead
    ON org_chart_position (position_type) WHERE position_type = 'AREA_LEAD';

-- At most one Staffelleiter per Staffel.
CREATE UNIQUE INDEX uq_org_chart_one_squadron_lead
    ON org_chart_position (org_unit_id) WHERE position_type = 'SQUADRON_LEAD';

-- At most one Stv. Kommandoleiter per Kommandoleiter.
CREATE UNIQUE INDEX uq_org_chart_one_deputy_per_command
    ON org_chart_position (parent_id) WHERE position_type = 'DEPUTY_COMMAND_LEAD';

-- A user appears at most once per scope: once per OrgUnit, and once in the area leadership.
CREATE UNIQUE INDEX uq_org_chart_user_per_unit
    ON org_chart_position (org_unit_id, user_id) WHERE org_unit_id IS NOT NULL;
CREATE UNIQUE INDEX uq_org_chart_user_in_area
    ON org_chart_position (user_id) WHERE org_unit_id IS NULL;
