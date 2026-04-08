ALTER TABLE material
    ADD COLUMN refined_material_id UUID;

ALTER TABLE material
    ADD CONSTRAINT fk_material_refined_material
    FOREIGN KEY (refined_material_id)
    REFERENCES material (id);
