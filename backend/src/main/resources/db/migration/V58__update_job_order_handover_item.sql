ALTER TABLE job_order_handover_item
    ADD COLUMN material_id UUID;

ALTER TABLE job_order_handover_item
    ADD COLUMN quality INTEGER;

-- Backfill data from inventory_item
UPDATE job_order_handover_item jhi
SET material_id = ii.material_id,
    quality = ii.quality
FROM inventory_item ii
WHERE jhi.inventory_item_id = ii.id;

-- Now set them to NOT NULL
ALTER TABLE job_order_handover_item
    ALTER COLUMN material_id SET NOT NULL;

ALTER TABLE job_order_handover_item
    ALTER COLUMN quality SET NOT NULL;

-- Make inventory_item_id nullable
ALTER TABLE job_order_handover_item
    ALTER COLUMN inventory_item_id DROP NOT NULL;

-- Update foreign key constraint to ON DELETE SET NULL
ALTER TABLE job_order_handover_item
    DROP CONSTRAINT fk_job_order_handover_item_inventory;

ALTER TABLE job_order_handover_item
    ADD CONSTRAINT fk_job_order_handover_item_inventory FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (id) ON DELETE SET NULL;

-- Add foreign key constraint for material_id
ALTER TABLE job_order_handover_item
    ADD CONSTRAINT fk_job_order_handover_item_material FOREIGN KEY (material_id) REFERENCES material (id);
