-- =====================================================================
-- V109 - SC Wiki sync R1 (foundation): uex_category reference table
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.6 introduces a reference table that
-- mirrors UEX's /categories endpoint (98 rows). The R2 UexItemSyncService
-- iterates the table to walk /items?id_category=<n> for every category;
-- the row also denormalises `section`, `is_game_related` and `is_mining`
-- so the `kind` derivation in §6.3.1 (Armor → ARMOR, "Vehicle Weapons"
-- → VEHICLE_WEAPON, …) is a cheap field read instead of a join against
-- a magic string list in code.
--
-- The PK is the UEX integer id (1..98+) to keep JOINs across game_item
-- (R2) and game_item_price (R7) predictable. The table starts empty;
-- R2's UexCategoryRefService populates it on first sync.
--
-- Rollback: DROP TABLE uex_category. R1 doesn't insert rows; R2 sync is
-- still idempotent against a re-created table.

CREATE TABLE IF NOT EXISTS uex_category (
    id              INTEGER PRIMARY KEY,
    version         BIGINT       NOT NULL DEFAULT 0,
    type            VARCHAR(16)  NOT NULL,
    section         VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    is_game_related BOOLEAN      NOT NULL,
    is_mining       BOOLEAN      NOT NULL,
    uex_synced_at   TIMESTAMP WITH TIME ZONE,
    uex_deleted_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_uex_category_type
        CHECK (type IN ('item', 'vehicle'))
);

CREATE INDEX IF NOT EXISTS idx_uex_category_section ON uex_category(section);
CREATE INDEX IF NOT EXISTS idx_uex_category_type    ON uex_category(type);
