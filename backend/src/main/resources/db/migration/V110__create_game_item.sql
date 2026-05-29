-- =====================================================================
-- V110 - SC Wiki sync R2: game_item table (joint UEX + Wiki entity)
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.3.1 introduces a single table that carries
-- both UEX-sourced and Wiki-sourced game items (armor, weapons, paints,
-- vehicle components, …) keyed by the shared in-game asset UUID. R2
-- ships the table + the UEX-side sync (UexItemSyncService); the Wiki-
-- side sync lands in R4.
--
-- Schema highlights:
--   * external_uuid UNIQUE — the cross-source join key (Plan §3.6's
--     241-test invariant: when UEX and Wiki both carry a UUID for the
--     same in-game asset, the UUIDs are identical).
--   * uex_item_id UNIQUE — UEX's integer id, the primary fast-path for
--     a re-sync against the same UEX catalogue.
--   * source_systems ENUM-ish VARCHAR with CHECK — UEX_ONLY (R2 default
--     for every newly created row), WIKI_ONLY (R4), BOTH (R4+).
--   * Wiki columns (scwiki_slug, classification, mass, dimension_*,
--     description_en/_de, …) stay nullable for R4. The R2 UexItemSync
--     never writes them.
--   * linked_ship_type_id captures the {@code id_vehicle} FK UEX
--     exposes for vehicle-bound items (paints, components). Resolved
--     at sync time from {@code ship_type.uex_vehicle_id}.
--
-- Rollback: DROP TABLE game_item CASCADE. No FK from other tables
-- (game_item_price lands in R7); the table is a leaf today.

CREATE TABLE IF NOT EXISTS game_item (
    id                          UUID PRIMARY KEY,
    version                     BIGINT                   NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    -- canonical / joint --------------------------------------------------
    -- external_uuid is the cross-source join key (Plan §3.6's 241-test invariant). It is
    -- intentionally NULLABLE in R2: ~30% of UEX items ship with an empty UUID (Avionics 100%,
    -- Decorations 88%, Liveries 42%, Armor ~33% — Plan §3.6 measurements). The plan's draft
    -- §6.3.1 schema declared NOT NULL; R2 relaxes that until R3's Wiki slug-fallback can
    -- backfill the missing UUIDs. UNIQUE still holds (Postgres tolerates multiple NULLs in a
    -- UNIQUE column) so a UEX row with a UUID can pair with at most one Wiki row.
    external_uuid               UUID,
    name                        VARCHAR(255)             NOT NULL,
    manufacturer_id             UUID REFERENCES manufacturer(id),
    kind                        VARCHAR(32)              NOT NULL,
    source_systems              VARCHAR(16)              NOT NULL DEFAULT 'UEX_ONLY',

    -- Wiki-sourced columns (nullable; R4 writes) -------------------------
    scwiki_slug                 VARCHAR(255),
    class_name                  VARCHAR(255),
    classification              VARCHAR(255),
    classification_label        VARCHAR(255),
    wiki_type                   VARCHAR(128),
    wiki_type_label             VARCHAR(128),
    wiki_sub_type               VARCHAR(128),
    wiki_sub_type_label         VARCHAR(128),
    size_class                  INTEGER,
    grade                       VARCHAR(32),
    rarity                      VARCHAR(32),
    mass                        DOUBLE PRECISION,
    dimension_x                 DOUBLE PRECISION,
    dimension_y                 DOUBLE PRECISION,
    dimension_z                 DOUBLE PRECISION,
    description_en              TEXT,
    description_de              TEXT,
    is_base_variant             BOOLEAN,
    is_craftable                BOOLEAN,
    scwiki_synced_at            TIMESTAMP WITH TIME ZONE,
    scwiki_deleted_at           TIMESTAMP WITH TIME ZONE,
    scwiki_game_version_seen    VARCHAR(64),

    -- UEX-sourced columns (R2 writes) ------------------------------------
    uex_item_id                 INTEGER,
    uex_slug                    VARCHAR(255),
    uex_category_id             INTEGER REFERENCES uex_category(id),
    uex_company_id              INTEGER,
    uex_vehicle_id              INTEGER,
    linked_ship_type_id         UUID REFERENCES ship_type(id),
    uex_color                   VARCHAR(64),
    uex_color2                  VARCHAR(64),
    uex_quality                 INTEGER,
    uex_url_store               VARCHAR(512),
    uex_screenshot              VARCHAR(512),
    is_exclusive_pledge         BOOLEAN,
    is_exclusive_subscriber     BOOLEAN,
    is_exclusive_concierge      BOOLEAN,
    uex_is_commodity            BOOLEAN,
    uex_is_harvestable          BOOLEAN,
    uex_notification            TEXT,
    uex_synced_at               TIMESTAMP WITH TIME ZONE,
    uex_deleted_at              TIMESTAMP WITH TIME ZONE,
    uex_game_version_seen       VARCHAR(64),

    CONSTRAINT uk_game_item_external_uuid UNIQUE (external_uuid),
    CONSTRAINT uk_game_item_uex_item_id UNIQUE (uex_item_id),
    CONSTRAINT chk_game_item_source_systems
        CHECK (source_systems IN ('UEX_ONLY', 'WIKI_ONLY', 'BOTH')),
    CONSTRAINT chk_game_item_kind
        CHECK (kind IN ('GENERIC', 'VEHICLE_ITEM', 'VEHICLE_WEAPON', 'WEAPON',
                        'WEAPON_ATTACHMENT', 'ARMOR', 'CLOTHING', 'FOOD'))
);

CREATE INDEX IF NOT EXISTS idx_game_item_kind            ON game_item(kind);
CREATE INDEX IF NOT EXISTS idx_game_item_classification  ON game_item(classification);
CREATE INDEX IF NOT EXISTS idx_game_item_manufacturer    ON game_item(manufacturer_id);
CREATE INDEX IF NOT EXISTS idx_game_item_uex_category    ON game_item(uex_category_id);
CREATE INDEX IF NOT EXISTS idx_game_item_linked_ship     ON game_item(linked_ship_type_id);
CREATE INDEX IF NOT EXISTS idx_game_item_source_systems  ON game_item(source_systems);
