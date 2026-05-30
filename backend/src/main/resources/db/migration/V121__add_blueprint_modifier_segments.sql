-- =====================================================================
-- V121 - Blueprint modifier value-segments (stepped / non-linear stats)
-- =====================================================================
-- Why: a blueprint_modifier may interpolate its output stat NON-linearly
-- across the ingredient quality band. The SC Wiki detail payload then ships
-- a value_segments[] array (each: quality_min/quality_max + the stat value
-- at the segment's start/end) and the simple modifier_at_min/max_quality
-- pair no longer describes the curve. V120 ignored that array; this table
-- captures it so the admin blueprint slider can compute the real stat value
-- at any quality (linear within the containing segment).
--
-- Table:
--   * blueprint_modifier_segment - one row per segment of a modifier's curve
--
-- Concurrency: the modifier owns its segment rows (cascade ALL +
-- orphanRemoval); the sync rebuilds the curve by mutating the managed
-- collection and relying on dirty-checking - no @Modifying bulk update runs
-- inside the per-blueprint loop, so the CLAUDE.md detach-clear trap does not
-- apply.
--
-- Rollback: DROP TABLE blueprint_modifier_segment. Additive; no existing
-- data is rewritten.

CREATE TABLE IF NOT EXISTS blueprint_modifier_segment (
    id                       UUID PRIMARY KEY,
    version                  BIGINT                   NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    requirement_modifier_id  UUID                     NOT NULL REFERENCES blueprint_requirement_modifier(id),
    order_index              INTEGER                  NOT NULL,
    quality_min              DOUBLE PRECISION,
    quality_max              DOUBLE PRECISION,
    modifier_at_start        DOUBLE PRECISION,
    modifier_at_end          DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_blueprint_modifier_segment_modifier
    ON blueprint_modifier_segment(requirement_modifier_id);
