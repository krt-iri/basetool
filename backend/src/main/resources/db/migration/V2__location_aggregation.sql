ALTER TABLE location DROP COLUMN IF EXISTS id_terminal;
ALTER TABLE location DROP COLUMN IF EXISTS star_system_id;

ALTER TABLE location ADD COLUMN city_id UUID REFERENCES city(id);
ALTER TABLE location ADD COLUMN space_station_id UUID REFERENCES space_station(id);
