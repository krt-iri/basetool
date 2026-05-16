-- Capture what UEX last reported on a terminal independently of the admin
-- override. The existing `has_loading_dock` / `is_auto_load` columns hold the
-- *effective* value (UEX-sourced or admin-pinned), and the override flags from
-- V75 cause the sync to skip writing the source column whenever an officer has
-- pinned it. That means once a pin is set we lose visibility into what UEX
-- currently claims, which is exactly the information `/admin/terminals` needs
-- to display so admins can decide whether to release the pin.
--
-- These columns are written unconditionally by `UexUniverseSyncService.syncTerminals()`
-- on every sweep (regardless of the override flags) so the page always has the
-- most recent UEX-reported value plus the sweep timestamp. `NULL` is meaningful:
-- the row has never been synced since the column was added (legacy or freshly
-- imported rows). The backfill below seeds the raw values from the effective
-- columns where no override is active — for those rows the effective value IS
-- the UEX value, so we can populate without waiting for the next sweep. Rows
-- with an active override stay `NULL` until UEX reports them again, which is
-- accurate: we genuinely don't know what UEX claims for them right now.

ALTER TABLE terminal
    ADD COLUMN uex_has_loading_dock BOOLEAN,
    ADD COLUMN uex_is_auto_load     BOOLEAN,
    ADD COLUMN uex_synced_at        TIMESTAMPTZ;

UPDATE terminal
SET uex_has_loading_dock = has_loading_dock
WHERE has_loading_dock_overridden = FALSE
  AND uex_has_loading_dock IS NULL;

UPDATE terminal
SET uex_is_auto_load = is_auto_load
WHERE is_auto_load_overridden = FALSE
  AND uex_is_auto_load IS NULL;
