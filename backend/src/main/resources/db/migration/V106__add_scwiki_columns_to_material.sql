-- =====================================================================
-- V106 - SC Wiki sync R1 (foundation): material cross-ref columns
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.1 adds the Star Citizen Wiki API
-- (https://api.star-citizen.wiki) as a second source for the commodity
-- catalogue. R1 is plumbing only — no Wiki sync runs yet — but the
-- material schema gains the columns the R3 commodity-merge service will
-- populate. Until R3 ships, every UEX-sourced row keeps its existing
-- visibility (is_visible = TRUE) and source_systems = UEX_ONLY.
--
-- Critical defaults (see SC_WIKI_SYNC_AGENT_PROMPT.md §5 pitfall #5):
--   * is_visible NOT NULL DEFAULT TRUE — existing UEX rows MUST stay
--     visible after the migration. Wiki-only rows (R3) explicitly write
--     FALSE so they don't pollute trading flows until admin review.
--   * source_systems NOT NULL DEFAULT 'UEX_ONLY' — matches reality of the
--     pre-Wiki catalogue. The R3 sync flips UEX_ONLY → BOTH when a Wiki
--     match is found.
--
-- Rollback: ALTER TABLE material DROP COLUMN <each column>. All columns
-- are additive; no row data is mutated.

ALTER TABLE material
    ADD COLUMN IF NOT EXISTS scwiki_uuid       UUID,
    ADD COLUMN IF NOT EXISTS scwiki_key        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scwiki_slug       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scwiki_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scwiki_deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS density_g_per_cc  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS instability       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS resistance        DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS is_visible        BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS source_systems    VARCHAR(16) NOT NULL DEFAULT 'UEX_ONLY';

-- scwiki_uuid is the cross-source join key (Wiki commodity UUID); the
-- UNIQUE constraint protects against an R3 race that would otherwise
-- pair the same Wiki commodity with two local material rows.
ALTER TABLE material
    ADD CONSTRAINT uk_material_scwiki_uuid UNIQUE (scwiki_uuid);

CREATE INDEX IF NOT EXISTS idx_material_source_systems ON material(source_systems);
CREATE INDEX IF NOT EXISTS idx_material_is_visible     ON material(is_visible);
