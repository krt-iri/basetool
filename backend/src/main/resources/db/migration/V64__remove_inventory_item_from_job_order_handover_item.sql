-- Remove the FK link from job_order_handover_item to inventory_item.
-- Rationale: A handover is a historical record. The corresponding inventory_item can be
-- removed at any time (e.g. when the stock is emptied or the warehouse is cleared), which
-- previously caused a FK constraint violation (fk_job_order_handover_item_inventory)
-- during the very flush that booked the handover. The relevant data (material, quality,
-- amount) is already snapshotted directly on the handover item (see V58), so the inventory
-- reference is no longer required.

ALTER TABLE job_order_handover_item
    DROP CONSTRAINT IF EXISTS fk_job_order_handover_item_inventory;

ALTER TABLE job_order_handover_item
    DROP COLUMN IF EXISTS inventory_item_id;
