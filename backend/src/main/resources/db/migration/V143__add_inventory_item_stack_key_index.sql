-- Append-only inventory (REQ-INV-001/002, ADR-0003) keeps every contribution as its own row and
-- collapses rows that share a stock identity only at read time. The grouped Lager views now
-- aggregate by the inventory natural key (the "stack key") in SQL, and the per-stack entries are
-- loaded lazily on expand, paginated. Both the per-stack GROUP BY and the per-stack entries lookup
-- filter on the same column tuple, so a single composite index makes them index-driven instead of
-- scanning the whole owner / org-unit slice as the append-only row count grows.
--
-- ddl-auto stays `validate`: Hibernate does not validate indexes, so no entity mapping changes.
CREATE INDEX IF NOT EXISTS idx_inventory_item_stack_key
    ON inventory_item (
        material_id,
        user_id,
        location_id,
        quality,
        job_order_id,
        mission_id,
        personal,
        owning_org_unit_id
    );

COMMENT ON INDEX idx_inventory_item_stack_key IS 'Composite index on the inventory natural key (stack identity: material, user, location, quality, job order, mission, personal, owning org unit). Backs the group-on-read per-stack GROUP BY and the lazy per-stack entries lookup introduced with ADR-0003 / REQ-INV-002.';
