-- V34__add_performance_indexes.sql
-- Optimierung: Indizes fuer häufig genutzte Fremdschluessel und Filterspalten.

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX IF NOT EXISTS idx_job_type_parent_id ON job_type(parent_id);
CREATE INDEX IF NOT EXISTS idx_location_city_id ON location(city_id);
CREATE INDEX IF NOT EXISTS idx_location_space_station_id ON location(space_station_id);
CREATE INDEX IF NOT EXISTS idx_ship_type_manufacturer_id ON ship_type(manufacturer_id);
CREATE INDEX IF NOT EXISTS idx_ship_ship_type_id ON ship(ship_type_id);
CREATE INDEX IF NOT EXISTS idx_ship_location_id ON ship(location_id);
CREATE INDEX IF NOT EXISTS idx_ship_owner_id ON ship(owner_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_material_id ON inventory_item(material_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_location_id ON inventory_item(location_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_user_id ON inventory_item(user_id);
CREATE INDEX IF NOT EXISTS idx_mission_parent_mission_id ON mission(parent_mission_id);

CREATE INDEX IF NOT EXISTS idx_mission_participant_mission_id ON mission_participant(mission_id);
CREATE INDEX IF NOT EXISTS idx_mission_participant_user_id ON mission_participant(user_id);

CREATE INDEX IF NOT EXISTS idx_mission_unit_mission_id ON mission_unit(mission_id);

CREATE INDEX IF NOT EXISTS idx_mission_crew_ship_id ON mission_crew(mission_ship_id);
CREATE INDEX IF NOT EXISTS idx_mission_crew_participant_id ON mission_crew(mission_participant_id);

CREATE INDEX IF NOT EXISTS idx_mission_crew_job_types_crew_id ON mission_crew_job_types(mission_crew_id);

CREATE INDEX IF NOT EXISTS idx_job_order_material_job_order_id ON job_order_material(job_order_id);
CREATE INDEX IF NOT EXISTS idx_job_order_material_material_id ON job_order_material(material_id);

CREATE INDEX IF NOT EXISTS idx_refinery_order_owner_id ON refinery_order(owner_id);
CREATE INDEX IF NOT EXISTS idx_refinery_order_location_id ON refinery_order(location_id);

CREATE INDEX IF NOT EXISTS idx_refinery_good_input_material_id ON refinery_good(input_material_id);
CREATE INDEX IF NOT EXISTS idx_refinery_good_output_material_id ON refinery_good(output_material_id);
CREATE INDEX IF NOT EXISTS idx_refinery_good_order_id ON refinery_good(refinery_order_id);

