-- Convert refinery good quantities back to double precision (SCU)
ALTER TABLE refinery_good
    ALTER COLUMN input_quantity TYPE DOUBLE PRECISION,
    ALTER COLUMN output_quantity TYPE DOUBLE PRECISION;
