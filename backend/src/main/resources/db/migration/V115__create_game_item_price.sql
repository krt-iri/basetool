-- =====================================================================
-- V115 - SC Wiki sync R7: UEX item price matrix
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.7 introduces game_item_price, the per-
-- (game_item, terminal) retail-price row populated by the R7
-- UexItemPriceSyncService from UEX /items_prices_all. Display-only and
-- feature-flagged (krt.uex.item-price-sync-enabled, default false), so the
-- table stays inert until an operator opts in.
--
-- V-NUMBER DRIFT: the plan §7 table pencilled this in as V114, but V113
-- went to R3's external_sync_report and V114 to R4's blueprint tables.
-- R7 takes the next free number, V115. The R8 is_manual_entry cleanup and
-- the R9 destructive drop shift to V116 / V117 accordingly.
--
-- LIVE-FEED NOTE (probed /items_prices_all on game 4.8.0): the endpoint
-- returns only id_item / id_terminal / price_buy / price_sell / date_added
-- / date_modified (plus name/uuid labels). price_rent, status_buy,
-- status_sell and game_version are kept per the §6.7 schema for
-- forward-compatibility (item detail carries rent/status via a richer
-- shape) but stay NULL under the current feed.
--
-- Rollback: DROP TABLE game_item_price. Additive; no existing table is
-- touched.

CREATE TABLE IF NOT EXISTS game_item_price (
    id             UUID PRIMARY KEY,
    version        BIGINT                   NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    game_item_id   UUID                     NOT NULL REFERENCES game_item(id),
    terminal_id    UUID                     NOT NULL REFERENCES terminal(id),
    price_buy      DOUBLE PRECISION,
    price_sell     DOUBLE PRECISION,
    price_rent     DOUBLE PRECISION,
    status_buy     INTEGER,
    status_sell    INTEGER,
    date_modified  BIGINT,
    game_version   VARCHAR(64),
    uex_synced_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_game_item_price_item_terminal UNIQUE (game_item_id, terminal_id)
);

CREATE INDEX IF NOT EXISTS idx_game_item_price_terminal ON game_item_price(terminal_id);
