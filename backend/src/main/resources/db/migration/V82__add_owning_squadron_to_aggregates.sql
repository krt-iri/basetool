-- =====================================================================
-- V82 - Aggregate-root squadron scope
-- =====================================================================
-- Why: mission / operation / ship / inventory_item / refinery_order
-- become squadron-scoped aggregate roots. owning_squadron_id gates read
-- and write access (admin always allowed). Job Orders are NOT in this
-- migration - they are an intentional cross-squadron workspace and get
-- their dual creating_/requesting_ columns in V83 (see
-- MULTI_SQUADRON_PLAN.md, sections 1 and 3.2).
--
-- Phase 1 of the two-phase rollout: column added as nullable + backfill
-- to IRIDIUM. NOT NULL tightening lands in V84, one release later, once
-- prod confirms the new code path persists the column on every write.

ALTER TABLE mission        ADD COLUMN owning_squadron_id UUID;
ALTER TABLE operation      ADD COLUMN owning_squadron_id UUID;
ALTER TABLE ship           ADD COLUMN owning_squadron_id UUID;
ALTER TABLE inventory_item ADD COLUMN owning_squadron_id UUID;
ALTER TABLE refinery_order ADD COLUMN owning_squadron_id UUID;

UPDATE mission        SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE operation      SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE ship           SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE inventory_item SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;
UPDATE refinery_order SET owning_squadron_id = '00000000-0000-0000-0000-000000000001' WHERE owning_squadron_id IS NULL;

ALTER TABLE mission
    ADD CONSTRAINT fk_mission_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);

ALTER TABLE operation
    ADD CONSTRAINT fk_operation_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);

ALTER TABLE ship
    ADD CONSTRAINT fk_ship_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);

ALTER TABLE inventory_item
    ADD CONSTRAINT fk_inventory_item_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);

ALTER TABLE refinery_order
    ADD CONSTRAINT fk_refinery_order_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES squadron(id);
