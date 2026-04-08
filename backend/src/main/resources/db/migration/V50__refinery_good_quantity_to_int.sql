-- Convert refinery good quantities back to integer (units)
ALTER TABLE refinery_good
    ALTER COLUMN input_quantity TYPE INTEGER USING ROUND(input_quantity)::INTEGER,
    ALTER COLUMN output_quantity TYPE INTEGER USING ROUND(output_quantity)::INTEGER;
