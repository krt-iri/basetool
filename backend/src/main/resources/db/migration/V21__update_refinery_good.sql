ALTER TABLE refinery_good DROP CONSTRAINT IF EXISTS fk_refinery_good_material;
ALTER TABLE refinery_good DROP COLUMN material_id;
ALTER TABLE refinery_good RENAME COLUMN quantity TO input_quantity;
ALTER TABLE refinery_good ADD COLUMN input_material_id UUID REFERENCES material(id);
ALTER TABLE refinery_good ADD COLUMN output_material_id UUID REFERENCES material(id);
ALTER TABLE refinery_good ADD COLUMN output_quantity INTEGER;
ALTER TABLE refinery_good ADD COLUMN quality INTEGER;

-- If there are existing rows, they will have NULL in the new NOT NULL columns.
-- We could add defaults here if necessary.

ALTER TABLE refining_method ADD COLUMN code VARCHAR(255);
ALTER TABLE refining_method ADD COLUMN rating_yield INTEGER;
ALTER TABLE refining_method ADD COLUMN rating_cost INTEGER;
ALTER TABLE refining_method ADD COLUMN rating_speed INTEGER;

CREATE TABLE refinery_yield (
    id UUID PRIMARY KEY,
    terminal_id UUID NOT NULL REFERENCES terminal(id),
    material_id UUID NOT NULL REFERENCES material(id),
    yield_bonus INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE
);
