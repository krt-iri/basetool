-- =====================================================================
-- V116 - SC Wiki sync R8: stamp source_systems = 'MANUAL' on legacy
--        admin-created materials
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §11 R8 / §7. Admin-created commodities were
-- historically marked by material.is_manual_entry = TRUE while their
-- source_systems stayed at the V106 backfill default ('UEX_ONLY'), which
-- is wrong for a row UEX never sourced. R9 drops is_manual_entry, so the
-- MANUAL provenance must first move into the canonical source_systems enum
-- (MaterialSourceSystem.MANUAL) — the value the admin "manual" badge reads
-- once is_manual_entry is gone. The enum value already exists, so this is a
-- pure data backfill with no schema change.
--
-- V-NUMBER DRIFT: the plan §7 pencilled this as V115, but R7 took V115 for
-- game_item_price, so the is_manual_entry backfill is V116; the R9
-- destructive drop (material.is_manual_entry + ship_type.description)
-- shifts to V117.
--
-- Idempotent: the WHERE clause skips rows already MANUAL, so a re-run (or a
-- run against a partially-applied DB) changes nothing. UEX adoption sets
-- is_manual_entry = FALSE (UexCommodityService's manual-entry handover), so
-- a since-adopted row is naturally excluded — Flyway runs this once anyway.
--
-- Rollback: there is no clean automatic inverse (the pre-flip per-row value
-- is not preserved; it was the incorrect 'UEX_ONLY' default). is_manual_entry
-- is left untouched (R9 drops it), so it remains the source of truth for a
-- manual reclassification, or restore from the pre-R8 backup.

UPDATE material
   SET source_systems = 'MANUAL'
 WHERE is_manual_entry = TRUE
   AND source_systems <> 'MANUAL';
