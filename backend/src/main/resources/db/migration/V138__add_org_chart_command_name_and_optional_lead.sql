-- Org chart follow-up (#385): name a Kommando(gruppe) and let it exist before a Kommandoleiter is
-- assigned. The COMMAND_LEAD row now models the Kommando itself — it carries an optional group
-- `name` and an optional holder (the Kommandoleiter). The Stv. Kommandoleiter and the Ensigns still
-- hang off that row via parent_id, so they can be filled while the Kommandoleiter seat is still
-- vacant. Only COMMAND_LEAD rows may carry a name or a null user; every other functional rank keeps
-- its mandatory holder. Purely additive: existing COMMAND_LEAD rows (holder set, name NULL) and all
-- other rows already satisfy both new CHECKs, so no data migration is required.

ALTER TABLE org_chart_position
    ADD COLUMN name VARCHAR(120);

ALTER TABLE org_chart_position
    ALTER COLUMN user_id DROP NOT NULL;

-- A null holder is only legal for a (still-leaderless) Kommando; every other rank needs its user.
ALTER TABLE org_chart_position
    ADD CONSTRAINT chk_org_chart_user
        CHECK (user_id IS NOT NULL OR position_type = 'COMMAND_LEAD');

-- A group name only makes sense on a Kommando row.
ALTER TABLE org_chart_position
    ADD CONSTRAINT chk_org_chart_name
        CHECK (name IS NULL OR position_type = 'COMMAND_LEAD');

-- The one-user-per-scope uniqueness must ignore the new null-holder Kommando rows: two leaderless
-- Kommandos in the same Staffel both carry a null user_id and must not collide. Postgres already
-- treats NULLs as distinct in a unique index, but spelling the predicate out keeps the intent
-- explicit and future-proofs against NULLS NOT DISTINCT being toggled on the table.
DROP INDEX uq_org_chart_user_per_unit;
CREATE UNIQUE INDEX uq_org_chart_user_per_unit
    ON org_chart_position (org_unit_id, user_id)
    WHERE org_unit_id IS NOT NULL AND user_id IS NOT NULL;

DROP INDEX uq_org_chart_user_in_area;
CREATE UNIQUE INDEX uq_org_chart_user_in_area
    ON org_chart_position (user_id)
    WHERE org_unit_id IS NULL AND user_id IS NOT NULL;
