-- Revert refinery good quantities back to integer (units) and ensure they are whole numbers > 0
ALTER TABLE refinery_good
    ALTER COLUMN input_quantity TYPE INTEGER USING ROUND(input_quantity)::INTEGER,
    ALTER COLUMN output_quantity TYPE INTEGER USING ROUND(output_quantity)::INTEGER;
