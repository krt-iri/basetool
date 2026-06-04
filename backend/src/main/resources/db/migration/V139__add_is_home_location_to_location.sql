-- Adds an admin-curated "home location" marker to the location reference table. It flags the
-- in-game player residences / primary spawns (Lorville, Orison, ...) so the hangar bulk
-- "set home location" action can offer a short, curated picker instead of the full location list.
-- The flag is maintained locally and is never written by the UEX universe sync, so it survives
-- every sweep (same as the existing `hidden` flag).
ALTER TABLE location ADD COLUMN is_home_location BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed the currently known in-game home locations. Location rows are created lazily by the UEX
-- sync (only when is_available_live = true), so this only flags rows that already exist; any not
-- yet present can be toggled later from the admin Locations page. Idempotent and tolerant of
-- spelling variants (rows whose name does not match are simply left untouched).
UPDATE location
   SET is_home_location = TRUE
 WHERE name IN (
   'Lorville', 'Loreville',
   'Orison',
   'New Babbage',
   'Area18', 'Area 18',
   'Levski',
   'Ruin Station',
   'Checkmate',
   'Orbituary'
 );
