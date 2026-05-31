-- R9 Step 4 (destructive, irreversible): drop the two legacy columns whose readers and writers
-- were migrated to the canonical replacements in R9 Steps 1-2 (PR #275, the stop-writing
-- predecessor required by this README's two-phase DROP rule).
--
--   * material.is_manual_entry  -> superseded by source_systems = 'MANUAL' (backfilled in V116).
--   * ship_type.description     -> superseded by description_en / description_de (R2 / R4).
--
-- The matching JPA entity fields (Material.isManualEntry, ShipType.description) are removed in the
-- same change, so ddl-auto=validate stays green: during the Steps 1-2 soak both the field and the
-- column exist; after this migration neither does. Neither column carries an index or a named
-- constraint beyond is_manual_entry's NOT NULL (dropped with the column), so a plain DROP COLUMN
-- suffices. IF EXISTS keeps the migration robust against partially-applied developer databases.
--
-- Pre-conditions (see SC_WIKI_SYNC_DESTRUCTIVE_ROADMAP.md Step 4): Steps 1-2 deployed and soaked
-- clean for ~one release window, git grep for both columns clean in the main source set, full DB
-- backup taken immediately before merge. Irreversible: the per-row data is gone once this runs.

ALTER TABLE material DROP COLUMN IF EXISTS is_manual_entry;
ALTER TABLE ship_type DROP COLUMN IF EXISTS description;
