ALTER TABLE inventory_item ADD COLUMN mission_id UUID;
ALTER TABLE inventory_item ADD CONSTRAINT fk_inventory_item_mission FOREIGN KEY (mission_id) REFERENCES mission(id);
