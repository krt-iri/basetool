-- =====================================================================
-- V107 - SC Wiki sync R1 (foundation): manufacturer cross-ref columns
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.4 adds Wiki / UEX cross-reference columns
-- on `manufacturer` so the R6 reconciliation service can pair Wiki
-- manufacturer UUIDs with the existing manufacturer rows (today seeded
-- only by hand). UEX's /companies endpoint exposes an integer
-- `id_company` and a few extra fields (industry, item / vehicle
-- manufacturer flags) that are also captured here for the R2 extension
-- of UexManufacturerService.
--
-- Until R6 ships, every column stays NULL on existing rows and the
-- application code keeps reading `name` / `abbreviation` as today.
--
-- Rollback: drop each ALTER. Additive only.

ALTER TABLE manufacturer
    ADD COLUMN IF NOT EXISTS uex_company_id          INTEGER,
    ADD COLUMN IF NOT EXISTS scwiki_uuid             UUID,
    ADD COLUMN IF NOT EXISTS scwiki_code             VARCHAR(64),
    ADD COLUMN IF NOT EXISTS industry                VARCHAR(128),
    ADD COLUMN IF NOT EXISTS is_item_manufacturer    BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_vehicle_manufacturer BOOLEAN,
    ADD COLUMN IF NOT EXISTS uex_synced_at           TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scwiki_synced_at        TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS uex_deleted_at          TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS scwiki_deleted_at       TIMESTAMP WITH TIME ZONE;

ALTER TABLE manufacturer
    ADD CONSTRAINT uk_manufacturer_uex_company_id UNIQUE (uex_company_id);

ALTER TABLE manufacturer
    ADD CONSTRAINT uk_manufacturer_scwiki_uuid UNIQUE (scwiki_uuid);
