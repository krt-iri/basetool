-- =====================================================================
-- V169 - Bank backfill (epic #692, REQ-ORG-019 / REQ-BANK-027): link the
--        legacy area_name-based AREA account to its Bereich and the unlinked
--        singleton CARTEL account to the Organisationsleitung.
-- =====================================================================
-- Why: V168 introduced the org_unit_id FK for AREA/CARTEL accounts but did NOT
-- backfill -- in the general case there is no reliable area_name -> Bereich
-- mapping. The prod soak check (ADR-0028) then found exactly one legacy AREA
-- account (org_unit_id IS NULL, area_name set) and one unlinked CARTEL account.
-- Both are invisible to the new Bereich/OL bank cascade (the seam matches on the
-- org_unit_id FK). Now that Bereiche + the OL are first-class org_unit rows with
-- stable, unique names, we can reconcile those two rows here so the owning
-- Bereichsleitung/OL gain their balance view + own-level booking request.
--
-- Matching is name-based and deliberately conservative:
--   * AREA: link to the Bereich whose name equals area_name (case-insensitive,
--     trimmed) ONLY when EXACTLY ONE such Bereich exists; clear area_name in the
--     SAME statement so chk_bank_account_owner_ref (for AREA: exactly one of
--     {org_unit_id, area_name}) holds at statement end.
--   * CARTEL: link the singleton CARTEL to the OL ONLY when EXACTLY ONE
--     ORGANISATIONSLEITUNG row exists.
-- Both updates are guarded by org_unit_id IS NULL (idempotent: an already-linked
-- row or a re-run is a no-op) and by the count = 1 guards (no match or an
-- ambiguous match leaves the money account untouched rather than mis-linking it).
-- In dev/test there is no matching legacy data, so this migration updates zero
-- rows.
--
-- Cardinality stays safe: uq_bank_account_org_unit caps one account per org unit
-- and the target Bereich/OL own no account yet, so there is no conflict. The
-- @Version column is intentionally left untouched (no concurrent JPA writer; a
-- raw bump is unnecessary). Schema is unchanged -- this is data only.
-- Rollback: re-run is a no-op; to undo, restore area_name and NULL org_unit_id
-- on the affected AREA/CARTEL rows. No data destroyed (area_name is overwritten
-- with NULL on the linked AREA row, so note it before reverting).

-- AREA: legacy area_name account -> its Bereich (exactly-one-match guard).
UPDATE bank_account ba
SET org_unit_id = (
        SELECT ou.id
        FROM org_unit ou
        WHERE ou.kind = 'BEREICH'
          AND lower(btrim(ou.name)) = lower(btrim(ba.area_name))
    ),
    area_name = NULL
WHERE ba.type = 'AREA'
  AND ba.org_unit_id IS NULL
  AND ba.area_name IS NOT NULL
  AND (
        SELECT count(*)
        FROM org_unit ou
        WHERE ou.kind = 'BEREICH'
          AND lower(btrim(ou.name)) = lower(btrim(ba.area_name))
      ) = 1;

-- CARTEL: unlinked singleton account -> the Organisationsleitung (only when the
-- OL is itself a singleton row).
UPDATE bank_account ba
SET org_unit_id = (SELECT ou.id FROM org_unit ou WHERE ou.kind = 'ORGANISATIONSLEITUNG')
WHERE ba.type = 'CARTEL'
  AND ba.org_unit_id IS NULL
  AND (SELECT count(*) FROM org_unit ou WHERE ou.kind = 'ORGANISATIONSLEITUNG') = 1;
