-- V51__add_on_delete_set_null_to_ship_id.sql
-- Add ON DELETE SET NULL to mission_unit.ship_id constraint

ALTER TABLE mission_unit
DROP CONSTRAINT IF EXISTS mission_unit_ship_id_fkey;

ALTER TABLE mission_unit
ADD CONSTRAINT mission_unit_ship_id_fkey
FOREIGN KEY (ship_id) REFERENCES ship(id)
ON DELETE SET NULL;
