TRUNCATE TABLE material_price;

ALTER TABLE material_price 
  DROP COLUMN location_id CASCADE;

ALTER TABLE material_price 
  ADD COLUMN terminal_id UUID NOT NULL REFERENCES terminal(id);

ALTER TABLE material_price 
  ADD CONSTRAINT material_price_material_id_terminal_id_key UNIQUE (material_id, terminal_id);
