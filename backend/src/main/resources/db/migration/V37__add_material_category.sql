CREATE TABLE material_category (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    version BIGINT,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE material ADD COLUMN category_id UUID;
ALTER TABLE material ADD CONSTRAINT fk_material_category FOREIGN KEY (category_id) REFERENCES material_category(id) ON DELETE SET NULL;
