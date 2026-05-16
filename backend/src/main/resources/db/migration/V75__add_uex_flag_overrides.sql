-- Override flags so admins can pin loading-dock / autoload values that UEX
-- reports incorrectly. The UEX sync skips writing the source column whenever
-- the matching `*_overridden` flag is TRUE, so manual corrections survive the
-- hourly sweep. Existing rows default to FALSE (i.e. UEX-managed).

ALTER TABLE terminal
    ADD COLUMN has_loading_dock_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_auto_load_overridden BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE city
    ADD COLUMN has_loading_dock_overridden BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE space_station
    ADD COLUMN has_loading_dock_overridden BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE outpost
    ADD COLUMN has_loading_dock_overridden BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE poi
    ADD COLUMN has_loading_dock_overridden BOOLEAN NOT NULL DEFAULT FALSE;
