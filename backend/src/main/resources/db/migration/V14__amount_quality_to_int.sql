-- Convert amount to integer and round down existing decimal values
ALTER TABLE inventory_item
    ALTER COLUMN amount TYPE BIGINT USING FLOOR(amount)::BIGINT;

ALTER TABLE job_order_material
    ALTER COLUMN amount TYPE BIGINT USING FLOOR(amount)::BIGINT;
