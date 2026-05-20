-- =====================================================================
-- V92 - Backfill indexes on FK columns added after V34
-- =====================================================================
-- Why: V34__add_performance_indexes.sql was the project's blanket FK
-- index sweep and matched the schema as of its release. Four FK columns
-- escaped the sweep:
--
--   * `inventory_item.job_order_id`  -- column added in V10, no index
--   * `inventory_item.mission_id`    -- column added in V25, no index
--   * `mission.operation_id`         -- column added in V39, no index
--   * `refinery_order.mission_id`    -- column existed since V1, missed
--                                       by V34
--
-- All four are hot list-path filters in production:
--   * MissionRepository.searchMissions filters on `m.operation.id`
--     (operation dropdown on the missions list).
--   * InventoryItemRepository.findByJobOrderIdOrdered drives the job
--     order detail page; the unlinkJobOrder / unlinkJobOrderMaterial
--     bulk updates also key off the same column.
--   * InventoryItem -> mission filters power the mission detail page's
--     linked inventory section; unlinkMissions bulk-clears by mission id.
--   * RefineryOrderRepository.findByMissionId / findByMissionIdIn fire
--     on the mission detail page's refinery summary; unlinkMissions
--     bulk-clears by the same key.
--
-- PostgreSQL falls back to a sequential scan on each call without these
-- indexes; on the prod tables (inventory_item and refinery_order are the
-- biggest churning tables) that surfaces as multi-second mission /
-- order / inventory page loads. The fix is mechanical -- B-tree indexes
-- on the FK columns -- and mirrors the style of V34 and V91.
--
-- Matches the index style of V34 / V91: IF NOT EXISTS so the migration
-- is idempotent against developer DBs that may have hand-created an
-- index, regular CREATE INDEX (no CONCURRENTLY) because Flyway runs
-- each migration inside a single transaction. Build time is negligible
-- on every environment we ship to.

CREATE INDEX IF NOT EXISTS idx_inventory_item_job_order_id ON inventory_item(job_order_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_mission_id   ON inventory_item(mission_id);
CREATE INDEX IF NOT EXISTS idx_mission_operation_id        ON mission(operation_id);
CREATE INDEX IF NOT EXISTS idx_refinery_order_mission_id   ON refinery_order(mission_id);
