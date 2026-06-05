-- =====================================================================
-- V140 - KRT P4K Reader: P4K catalog-import source lane
-- =====================================================================
-- Why: the external "KRT P4K Reader" tool extracts the real Star Citizen
-- master data from the game's Data/Game2.dcb (DataForge) into one JSON
-- catalog. An admin uploads that JSON and the new P4kImportService
-- *enriches and reconciles* the existing game_item / ship_type /
-- manufacturer / material / blueprint rows by the DataForge __ref GUID
-- (= external_uuid for items/ships, scwiki_uuid for manufacturers /
-- commodities / blueprints), and can additionally seed brand-new rows for
-- genuinely-new game data UEX / SC-Wiki do not carry yet (admin opt-in,
-- filtered to real player-facing records, inserted as source = P4K).
--
-- This migration gives every one of those five tables its own P4K
-- provenance lane so the import is auditable and idempotent without
-- disturbing the UEX / SC-Wiki lanes:
--   * p4k_uuid       — the DataForge __ref GUID the import observed for
--                      the row. Kept ALONGSIDE external_uuid / scwiki_uuid
--                      (not in place of): the importer backfills the
--                      canonical UUID only when it is NULL and unclaimed,
--                      but always records the P4K-observed GUID here so a
--                      UUID disagreement between UEX/Wiki and the game DCB
--                      stays visible. Deliberately NOT UNIQUE — conflicting
--                      rows may legitimately carry the same P4K GUID until
--                      reconciled (the "keep both + report" policy).
--   * p4k_synced_at  — last successful import touch; a non-NULL value is
--                      how a row signals P4K participation (source_systems
--                      is intentionally left untouched on enriched rows).
--   * p4k_deleted_at — soft-delete marker, reserved for a future orphan
--                      sweep (no writer yet).
--
-- source_systems gains a 'P4K' member on game_item / ship_type so the
-- CHECK accepts it for the rows the opt-in seed inserts as P4K-owned (and
-- should any future flow set it explicitly); the existing
-- named CHECKs are DROP+ADD'ed with the extended IN-list. material has
-- no source_systems CHECK in the DB today (V106 added the column + index
-- but never a CHECK), so there is nothing to alter there — the enum still
-- gains the value at the Java layer for symmetry.
--
-- external_sync_report.source_system gains 'P4K' (the import writes its
-- backfill / conflict findings through SyncReportService with
-- source = P4K), so its V113 CHECK is extended the same way.
--
-- Two functional indexes back the import's class_name fallback lookups
-- (findByClassNameIgnoreCase compiles to upper(class_name) = upper(?)):
--   * idx_game_item_class_name_upper
--   * idx_ship_type_class_name_upper
--
-- Rollback: drop each ADD COLUMN, drop the two functional indexes, and
-- restore each CHECK to its pre-V140 IN-list. Additive only — no row data
-- is mutated.

-- ── P4K provenance columns (additive, O(1) metadata-only adds) ──
ALTER TABLE game_item
    ADD COLUMN IF NOT EXISTS p4k_uuid       UUID,
    ADD COLUMN IF NOT EXISTS p4k_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS p4k_deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE ship_type
    ADD COLUMN IF NOT EXISTS p4k_uuid       UUID,
    ADD COLUMN IF NOT EXISTS p4k_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS p4k_deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE manufacturer
    ADD COLUMN IF NOT EXISTS p4k_uuid       UUID,
    ADD COLUMN IF NOT EXISTS p4k_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS p4k_deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE material
    ADD COLUMN IF NOT EXISTS p4k_uuid       UUID,
    ADD COLUMN IF NOT EXISTS p4k_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS p4k_deleted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE blueprint
    ADD COLUMN IF NOT EXISTS p4k_uuid       UUID,
    ADD COLUMN IF NOT EXISTS p4k_synced_at  TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS p4k_deleted_at TIMESTAMP WITH TIME ZONE;

-- ── Extend the source_systems CHECKs to admit 'P4K' (DROP + ADD) ──
-- game_item: the CHECK was created inline with the table in V110.
ALTER TABLE game_item
    DROP CONSTRAINT IF EXISTS chk_game_item_source_systems;
ALTER TABLE game_item
    ADD CONSTRAINT chk_game_item_source_systems
        CHECK (source_systems IN ('UEX_ONLY', 'WIKI_ONLY', 'BOTH', 'P4K'));

-- ship_type: the CHECK was added in V111.
ALTER TABLE ship_type
    DROP CONSTRAINT IF EXISTS chk_ship_type_source_systems;
ALTER TABLE ship_type
    ADD CONSTRAINT chk_ship_type_source_systems
        CHECK (source_systems IN ('UEX_ONLY', 'WIKI_ONLY', 'BOTH', 'P4K'));

-- material: V106 created source_systems with a DEFAULT but NO CHECK, so
-- there is no constraint to extend here. The MaterialSourceSystem enum
-- (incl. MANUAL and the new P4K) remains the source of truth, matching
-- the pre-V140 state.

-- ── Extend external_sync_report.source_system to admit 'P4K' ──
-- The CHECK was created in V113 as ('UEX', 'SCWIKI').
ALTER TABLE external_sync_report
    DROP CONSTRAINT IF EXISTS chk_external_sync_report_source_system;
ALTER TABLE external_sync_report
    ADD CONSTRAINT chk_external_sync_report_source_system
        CHECK (source_system IN ('UEX', 'SCWIKI', 'P4K'));

-- ── Functional indexes for the case-insensitive class_name fallback ──
-- findByClassNameIgnoreCase on game_item / ship_type compiles to
-- upper(class_name) = upper(?); a functional index on upper(class_name)
-- keeps that backfill lookup off a sequential scan.
CREATE INDEX IF NOT EXISTS idx_game_item_class_name_upper
    ON game_item (upper(class_name));

CREATE INDEX IF NOT EXISTS idx_ship_type_class_name_upper
    ON ship_type (upper(class_name));
