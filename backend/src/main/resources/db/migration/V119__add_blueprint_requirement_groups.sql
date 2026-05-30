-- =====================================================================
-- V119 - Blueprint requirement groups (crafting stat contributions)
-- =====================================================================
-- Why: the SC Wiki blueprint DETAIL endpoint (/api/blueprints/{uuid})
-- exposes requirement_groups[] — named build slots (FRAME, EMITTER, ...)
-- that each bundle the ingredient(s) filling the slot with the stat
-- contributions ("modifiers") that slot makes to the crafted item. The
-- list endpoint (which the R4 sync used so far) omits this block, so the
-- existing blueprint / blueprint_ingredient tables only hold name / kind /
-- quantity. This migration adds the recipe-stat graph so the admin
-- blueprint page can show, per ingredient slot, which output stats it
-- delivers and across which quality band.
--
-- Tables:
--   * blueprint_requirement_group   — one row per build slot of a blueprint
--   * blueprint_requirement_modifier — one row per stat the slot contributes
--   * blueprint_summary_property     — de-duplicated roll-up of affected stats
-- Column additions:
--   * blueprint_ingredient.requirement_group_id — links an ingredient line to
--     its slot (NULL for rows synced from the flat list fallback) + min_quality
--   * blueprint.dismantle_time_seconds / dismantle_efficiency
--
-- Concurrency: the blueprint owns the new group / summary rows (cascade ALL +
-- orphanRemoval); each group owns its modifier rows. The sync rebuilds the
-- graph by mutating the managed collections and relying on dirty-checking —
-- no @Modifying bulk update runs inside the per-blueprint loop, so the
-- CLAUDE.md detach-clear trap does not apply.
--
-- Rollback: DROP the modifier table, then the summary + group tables (FK
-- order), drop the two ALTERs. All additive; no existing data is rewritten.

CREATE TABLE IF NOT EXISTS blueprint_requirement_group (
    id              UUID PRIMARY KEY,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    blueprint_id    UUID                     NOT NULL REFERENCES blueprint(id),
    order_index     INTEGER                  NOT NULL,
    group_key       VARCHAR(255),
    name            VARCHAR(255),
    kind            VARCHAR(16),
    required_count  INTEGER
);

CREATE INDEX IF NOT EXISTS idx_blueprint_requirement_group_blueprint
    ON blueprint_requirement_group(blueprint_id);

CREATE TABLE IF NOT EXISTS blueprint_requirement_modifier (
    id                       UUID PRIMARY KEY,
    version                  BIGINT                   NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    requirement_group_id     UUID                     NOT NULL REFERENCES blueprint_requirement_group(id),
    order_index              INTEGER                  NOT NULL,
    property_key             VARCHAR(255),
    label                    VARCHAR(255),
    better_when              VARCHAR(16),
    quality_min              DOUBLE PRECISION,
    quality_max              DOUBLE PRECISION,
    modifier_at_min_quality  DOUBLE PRECISION,
    modifier_at_max_quality  DOUBLE PRECISION,
    value_range_type         VARCHAR(32)
);

CREATE INDEX IF NOT EXISTS idx_blueprint_requirement_modifier_group
    ON blueprint_requirement_modifier(requirement_group_id);

CREATE TABLE IF NOT EXISTS blueprint_summary_property (
    id            UUID PRIMARY KEY,
    version       BIGINT                   NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    blueprint_id  UUID                     NOT NULL REFERENCES blueprint(id),
    order_index   INTEGER                  NOT NULL,
    property_key  VARCHAR(255),
    label         VARCHAR(255),
    better_when   VARCHAR(16)
);

CREATE INDEX IF NOT EXISTS idx_blueprint_summary_property_blueprint
    ON blueprint_summary_property(blueprint_id);

ALTER TABLE blueprint
    ADD COLUMN IF NOT EXISTS dismantle_time_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS dismantle_efficiency   DOUBLE PRECISION;

ALTER TABLE blueprint_ingredient
    ADD COLUMN IF NOT EXISTS requirement_group_id UUID REFERENCES blueprint_requirement_group(id),
    ADD COLUMN IF NOT EXISTS min_quality          INTEGER;

CREATE INDEX IF NOT EXISTS idx_blueprint_ingredient_requirement_group
    ON blueprint_ingredient(requirement_group_id);
