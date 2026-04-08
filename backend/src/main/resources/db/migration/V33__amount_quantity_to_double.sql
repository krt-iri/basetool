-- Convert amounts and quantities to double precision for 3 decimal places
ALTER TABLE inventory_item
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::DOUBLE PRECISION;

ALTER TABLE job_order_material
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::DOUBLE PRECISION;

ALTER TABLE refinery_good
    ALTER COLUMN input_quantity TYPE DOUBLE PRECISION USING input_quantity::DOUBLE PRECISION,
    ALTER COLUMN output_quantity TYPE DOUBLE PRECISION USING output_quantity::DOUBLE PRECISION;
