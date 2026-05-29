-- =====================================================================
-- V111 - SC Wiki sync R2: rich vehicle data on ship_type
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.5 hardens ship_type to the same joint
-- UEX + Wiki shape used by game_item. R2 wires the UEX side (the
-- rewritten UexVehicleService populates the 36 is_* flags, dimensions,
-- fuel, urls, descriptions from the extended UexVehicleDto); the Wiki
-- side (cargo_grids, game_name, …) lands in R4.
--
-- external_uuid + uex_vehicle_id are the cross-source join keys. The
-- hardened UexVehicleService backfills external_uuid on existing rows
-- via its byNameIgnoreCase fallback (§8.5), so no separate V112 data
-- migration is required — the first post-deploy sync after R2 sets
-- both columns on every match. Rows that don't match by name (admin-
-- created entries, retired ships) stay UUID-less and surface in the
-- sync report once R3 ships it.
--
-- The legacy synthesized {@code description} column stays for one
-- release of back-compat; the post-soak destructive cleanup (R9) drops
-- it together with material.is_manual_entry.
--
-- Rollback: DROP COLUMN reverse order. Additive only; no data is
-- mutated by the migration itself.

ALTER TABLE ship_type
    ADD COLUMN IF NOT EXISTS external_uuid           UUID,
    ADD COLUMN IF NOT EXISTS uex_vehicle_id          INTEGER,
    ADD COLUMN IF NOT EXISTS uex_slug                VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scwiki_slug             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_full               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS game_name               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS class_name              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS crew_min                INTEGER,
    ADD COLUMN IF NOT EXISTS crew_max                INTEGER,
    ADD COLUMN IF NOT EXISTS mass                    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS mass_total              DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS width                   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS height                  DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS length_m                DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS pad_type                VARCHAR(8),
    ADD COLUMN IF NOT EXISTS fuel_quantum            DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS fuel_hydrogen           DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS vehicle_inventory_scu   DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS ore_capacity            DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS container_sizes         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS max_medical_tier        INTEGER,
    ADD COLUMN IF NOT EXISTS health                  INTEGER,
    ADD COLUMN IF NOT EXISTS shield_hp               INTEGER,
    ADD COLUMN IF NOT EXISTS is_addon                BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_boarding             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_bomber               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_cargo                BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_carrier              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_civilian             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_concept              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_construction         BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_datarunner           BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_docking              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_emp                  BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_exploration          BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_ground_vehicle       BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_hangar               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_industrial           BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_interdiction         BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_loading_dock         BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_medical              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_military             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_mining               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_passenger            BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_qed                  BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_quantum_capable      BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_racing               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_refinery             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_refuel               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_repair               BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_research             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_salvage              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_scanning             BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_science              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_showdown_winner      BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_spaceship            BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_starter              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_stealth              BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_tractor_beam         BOOLEAN,
    ADD COLUMN IF NOT EXISTS url_store               VARCHAR(512),
    ADD COLUMN IF NOT EXISTS url_brochure            VARCHAR(512),
    ADD COLUMN IF NOT EXISTS url_hotsite             VARCHAR(512),
    ADD COLUMN IF NOT EXISTS url_photo               VARCHAR(512),
    ADD COLUMN IF NOT EXISTS url_video               VARCHAR(512),
    ADD COLUMN IF NOT EXISTS url_wiki                VARCHAR(512),
    ADD COLUMN IF NOT EXISTS description_en          TEXT,
    ADD COLUMN IF NOT EXISTS description_de          TEXT,
    ADD COLUMN IF NOT EXISTS uex_synced_at           TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scwiki_synced_at        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS uex_deleted_at          TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scwiki_deleted_at       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS source_systems          VARCHAR(16) NOT NULL DEFAULT 'UEX_ONLY';

ALTER TABLE ship_type
    ADD CONSTRAINT uk_ship_type_external_uuid UNIQUE (external_uuid);

ALTER TABLE ship_type
    ADD CONSTRAINT uk_ship_type_uex_vehicle_id UNIQUE (uex_vehicle_id);

ALTER TABLE ship_type
    ADD CONSTRAINT chk_ship_type_source_systems
        CHECK (source_systems IN ('UEX_ONLY', 'WIKI_ONLY', 'BOTH'));

CREATE INDEX IF NOT EXISTS idx_ship_type_source_systems ON ship_type(source_systems);
