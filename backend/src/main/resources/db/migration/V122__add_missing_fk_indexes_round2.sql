-- =====================================================================
-- V122 - Second backfill of indexes on FK / filter columns
-- =====================================================================
-- Why: V34 (blanket FK sweep) and V92 (first backfill) covered the big
-- churning aggregates, but a query-pattern audit of every repository
-- surfaced a further set of FK columns that PostgreSQL does NOT index
-- automatically and that real queries key off. They split into two tiers.
--
-- HIGH (growing tables + hot or cascade-driven lookups):
--   * job_order_handover.job_order_id
--       JobOrderHandoverRepository.findByJobOrderId drives every job order
--       handover view; the column has only its FK constraint, no index.
--   * job_order_handover_item.job_order_handover_id
--       FK declared ON DELETE CASCADE (V57). Without an index PostgreSQL
--       sequentially scans the child table on every parent handover delete.
--   * mission_unit.ship_id
--       MissionUnitRepository.findByShipId is the "is this ship still used
--       in a unit?" guard before ship deletion / reassignment; the column
--       is also ON DELETE SET NULL, which scans the table on each ship
--       delete when unindexed.
--   * refinery_yield (terminal_id, material_id)
--       RefineryYieldRepository.findByTerminalIdAndMaterialId is the
--       per-(terminal, material) yield lookup during refinery-order
--       enrichment. Plain composite, NOT unique: refinery_yield is fed by
--       the UEX universe sync and a DB-level UNIQUE on external data has
--       bitten this codebase before (#278/#279). The lookup gains nothing
--       from uniqueness, only from the composite ordering.
--
-- MEDIUM (FK guards / joins on moderately growing tables):
--   * mission_participant.desired_mission_job_type_id
--   * mission_participant.planned_task_job_type_id
--       existsBy/findBy guards run before a JobType is deleted from
--       Stammdaten; mission_participant grows with every mission signup.
--   * mission_unit.ship_type_id
--       ShipType in-use guard before ShipType deletion.
--   * material.category_id
--       Joined + grouped in MaterialRepository.getMaterialPriceOverview.
--   * material_price.terminal_id
--       Terminal-driven joins (matrix / auto-load price queries). The
--       existing UNIQUE(material_id, terminal_id) (V7) leads with
--       material_id, so terminal_id is not independently usable there.
--   * operation_payout_status.paid_out_by_user_id
--       FK ON DELETE SET NULL (V78) hit on every user deletion; only
--       operation_id was indexed.
--   * job_order_handover_item.material_id
--       FK guard / lookup on the handover snapshot row; unindexed since
--       V58 reshaped the table around material_id (V64 dropped the old
--       inventory_item_id link).
--
-- Deliberately skipped: FK columns on tiny reference tables (rank
-- requirement, mission frequency, refining method) where a sequential
-- scan over a few dozen rows beats an index probe, and game_item_price
-- /material_price.material_id which are already covered as the leading
-- column of an existing UNIQUE.
--
-- Style mirrors V34 / V91 / V92: IF NOT EXISTS for idempotency against
-- developer DBs with hand-created indexes, plain CREATE INDEX (no
-- CONCURRENTLY) because Flyway wraps each migration in a transaction.
-- Build cost is negligible on every environment we ship to.

-- HIGH ----------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_job_order_handover_job_order_id
    ON job_order_handover(job_order_id);
CREATE INDEX IF NOT EXISTS idx_job_order_handover_item_handover_id
    ON job_order_handover_item(job_order_handover_id);
CREATE INDEX IF NOT EXISTS idx_mission_unit_ship_id
    ON mission_unit(ship_id);
CREATE INDEX IF NOT EXISTS idx_refinery_yield_terminal_material
    ON refinery_yield(terminal_id, material_id);

-- MEDIUM --------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_mission_participant_desired_job_type
    ON mission_participant(desired_mission_job_type_id);
CREATE INDEX IF NOT EXISTS idx_mission_participant_planned_job_type
    ON mission_participant(planned_task_job_type_id);
CREATE INDEX IF NOT EXISTS idx_mission_unit_ship_type_id
    ON mission_unit(ship_type_id);
CREATE INDEX IF NOT EXISTS idx_material_category_id
    ON material(category_id);
CREATE INDEX IF NOT EXISTS idx_material_price_terminal_id
    ON material_price(terminal_id);
CREATE INDEX IF NOT EXISTS idx_operation_payout_status_paid_out_by_user
    ON operation_payout_status(paid_out_by_user_id);
CREATE INDEX IF NOT EXISTS idx_job_order_handover_item_material_id
    ON job_order_handover_item(material_id);
