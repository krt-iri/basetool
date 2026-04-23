-- Add optional free-text note/remark to inventory items.
ALTER TABLE inventory_item ADD COLUMN note VARCHAR(1000);
