-- =====================================================================
-- V162 - manufacturer UEX-company alias table + dedup of UEX duplicates
-- =====================================================================
-- Why: UEX ships several DISTINCT /companies records for the same real
-- manufacturer. V158 / REQ-DATA-004 stopped the abbreviation-UNIQUE crash by
-- letting each such company keep its OWN manufacturer row, but that SPLIT a
-- brand across two rows: observed in prod (v0.5.9) id 87 "Esperia" carried 43
-- items while id 278 "Esperia Incorporation" carried the 7 ships + 15 more
-- items; likewise 70 "Denim Manufacture Corporation" / 287 "DMC" and 62
-- "Covalex Shipping" / 293 "Covalex". The item sync resolves the manufacturer
-- by `id_company` and the vehicle sync by `id_company` too, but the two
-- surfaces reference DIFFERENT ids for the same brand, so no single row keyed
-- on one `uex_company_id` can serve both.
--
-- Fix (ADR-0023, amends REQ-DATA-004): a manufacturer may own MANY UEX company
-- ids. `manufacturer_uex_company` maps every id (canonical + duplicate) to the
-- one surviving row; the item/vehicle syncs resolve through it, so every
-- id-variant of a brand reunites on one manufacturer. The canonical company
-- (lowest `uex_company_id` of a brand) keeps the row's display identity via
-- `manufacturer.uex_company_id`.
--
-- This migration also COLLAPSES the existing duplicate rows: it repoints the
-- ship_type / game_item FKs onto the canonical row, carries the SC Wiki / P4K
-- cross-source links over, ORs the item/vehicle flags, deletes the loser rows
-- and seeds the alias table. Every statement is a no-op on a fresh (test) DB.
--
-- Rollback: up-only. A corrective forward migration would re-split the rows,
-- which there is no reason to do.

-- ---------------------------------------------------------------------
-- 1) Alias table: one UEX company id -> exactly one manufacturer.
-- ---------------------------------------------------------------------
CREATE TABLE manufacturer_uex_company (
    uex_company_id  INTEGER PRIMARY KEY,
    manufacturer_id UUID NOT NULL REFERENCES manufacturer (id) ON DELETE CASCADE
);

CREATE INDEX idx_manufacturer_uex_company_manufacturer
    ON manufacturer_uex_company (manufacturer_id);

COMMENT ON TABLE manufacturer_uex_company IS
    'Maps each UEX /companies id (canonical + the duplicate records UEX ships for the same brand) to the one surviving manufacturer row, so the item and vehicle syncs reunite a brand''s ships and items. See ADR-0023 / REQ-DATA-004.';

-- ---------------------------------------------------------------------
-- 2) Collapse duplicate manufacturer rows that share a (case-insensitive)
--    abbreviation onto the canonical row = the lowest uex_company_id of the
--    group. Only rows already claimed by UEX (uex_company_id IS NOT NULL)
--    participate; hand-seeded / P4K-only rows (uex_company_id NULL) are left
--    untouched.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE _mfr_dedup ON COMMIT DROP AS
WITH grp AS (
    SELECT id,
           uex_company_id,
           lower(abbreviation)                                 AS abbr_key,
           MIN(uex_company_id) OVER (PARTITION BY lower(abbreviation)) AS canon_uex_id,
           COUNT(*)            OVER (PARTITION BY lower(abbreviation)) AS n
    FROM manufacturer
    WHERE uex_company_id IS NOT NULL
)
SELECT loser.id             AS loser_id,
       loser.uex_company_id AS loser_uex_id,
       canon.id             AS canonical_id
FROM grp loser
JOIN grp canon
  ON canon.abbr_key = loser.abbr_key
 AND canon.uex_company_id = loser.canon_uex_id
WHERE loser.n > 1
  AND loser.uex_company_id <> loser.canon_uex_id;

-- 2a) Repoint child FKs from the loser manufacturers onto the canonical row.
UPDATE ship_type st
   SET manufacturer_id = d.canonical_id
  FROM _mfr_dedup d
 WHERE st.manufacturer_id = d.loser_id;

UPDATE game_item gi
   SET manufacturer_id = d.canonical_id
  FROM _mfr_dedup d
 WHERE gi.manufacturer_id = d.loser_id;

-- 2b) Carry SC Wiki / P4K cross-source links from a loser onto the canonical
--     row when the canonical lacks them. scwiki_uuid is UNIQUE, so capture the
--     loser values, free them on the loser, then COALESCE them onto canonical.
CREATE TEMP TABLE _mfr_xref ON COMMIT DROP AS
SELECT DISTINCT ON (d.canonical_id)
       d.canonical_id,
       m.scwiki_uuid,
       m.scwiki_code,
       m.scwiki_synced_at,
       m.p4k_uuid,
       m.p4k_synced_at
FROM _mfr_dedup d
JOIN manufacturer m ON m.id = d.loser_id
WHERE m.scwiki_uuid IS NOT NULL
   OR m.p4k_uuid IS NOT NULL
ORDER BY d.canonical_id, m.uex_company_id;

UPDATE manufacturer m
   SET scwiki_uuid = NULL,
       p4k_uuid    = NULL
  FROM _mfr_dedup d
 WHERE m.id = d.loser_id;

UPDATE manufacturer c
   SET scwiki_uuid      = COALESCE(c.scwiki_uuid, x.scwiki_uuid),
       scwiki_code      = COALESCE(c.scwiki_code, x.scwiki_code),
       scwiki_synced_at = COALESCE(c.scwiki_synced_at, x.scwiki_synced_at),
       p4k_uuid         = COALESCE(c.p4k_uuid, x.p4k_uuid),
       p4k_synced_at    = COALESCE(c.p4k_synced_at, x.p4k_synced_at)
  FROM _mfr_xref x
 WHERE c.id = x.canonical_id;

-- 2c) OR the manufacturer-surface flags onto the canonical row (a duplicate is
--     often the vehicle-side record while the canonical is the item-side one).
UPDATE manufacturer c
   SET is_item_manufacturer =
           COALESCE(c.is_item_manufacturer, FALSE) OR COALESCE(agg.item_any, FALSE),
       is_vehicle_manufacturer =
           COALESCE(c.is_vehicle_manufacturer, FALSE) OR COALESCE(agg.veh_any, FALSE)
  FROM (
      SELECT d.canonical_id,
             bool_or(COALESCE(m.is_item_manufacturer, FALSE))    AS item_any,
             bool_or(COALESCE(m.is_vehicle_manufacturer, FALSE)) AS veh_any
      FROM _mfr_dedup d
      JOIN manufacturer m ON m.id = d.loser_id
      GROUP BY d.canonical_id
  ) agg
 WHERE c.id = agg.canonical_id;

-- 2d) Delete the now-orphaned loser manufacturer rows (children already
--     repointed, cross-source links carried over).
DELETE FROM manufacturer m
 USING _mfr_dedup d
 WHERE m.id = d.loser_id;

-- ---------------------------------------------------------------------
-- 3) Seed the alias table.
-- ---------------------------------------------------------------------
-- 3a) Every surviving manufacturer with a uex_company_id maps to itself.
INSERT INTO manufacturer_uex_company (uex_company_id, manufacturer_id)
SELECT uex_company_id, id
FROM manufacturer
WHERE uex_company_id IS NOT NULL
ON CONFLICT (uex_company_id) DO NOTHING;

-- 3b) Every former-loser company id now maps to the canonical manufacturer.
INSERT INTO manufacturer_uex_company (uex_company_id, manufacturer_id)
SELECT loser_uex_id, canonical_id
FROM _mfr_dedup
ON CONFLICT (uex_company_id) DO UPDATE
   SET manufacturer_id = EXCLUDED.manufacturer_id;
