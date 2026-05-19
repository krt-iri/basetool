-- =====================================================================
-- V91 - Indexes on squadron-scope columns
-- =====================================================================
-- Why: V81/V82/V83/V85 introduced the squadron-scope FK columns
-- (app_user.squadron_id, owning_squadron_id on 5 aggregates +
-- promotion_topic, creating_/requesting_squadron_id on job_order) but
-- only created the FK constraints — never an index. Every list endpoint
-- now filters by one of those columns (MissionRepository.searchMissions,
-- OperationRepository.findAllScoped, the hangar / inventory / refinery
-- list services, JobOrder list paths) and PostgreSQL falls back to a
-- sequential scan on each call, which manifests as multi-second page
-- loads on the missions and operations pages after the multi-squadron
-- rollout.
--
-- Matches the index style of V34__add_performance_indexes.sql:
-- IF NOT EXISTS so the migration is idempotent against developer DBs
-- that may have hand-created the index, regular CREATE INDEX (no
-- CONCURRENTLY) because Flyway runs migrations inside a single
-- transaction and the affected tables are small enough that the brief
-- exclusive lock during build is acceptable on every environment we
-- ship to.

-- Aggregate-root squadron scope (V82 columns).
CREATE INDEX IF NOT EXISTS idx_mission_owning_squadron_id        ON mission(owning_squadron_id);
CREATE INDEX IF NOT EXISTS idx_operation_owning_squadron_id      ON operation(owning_squadron_id);
CREATE INDEX IF NOT EXISTS idx_ship_owning_squadron_id           ON ship(owning_squadron_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_owning_squadron_id ON inventory_item(owning_squadron_id);
CREATE INDEX IF NOT EXISTS idx_refinery_order_owning_squadron_id ON refinery_order(owning_squadron_id);

-- Composite index for Mission's cross-staffel-public-escape clause:
-- `WHERE owning_squadron.id = :scope OR is_internal = false`. The
-- single-column index above already covers the equality predicate; the
-- composite version lets PostgreSQL serve the combined filter without a
-- bitmap-OR join across two indexes.
CREATE INDEX IF NOT EXISTS idx_mission_owning_squadron_internal  ON mission(owning_squadron_id, is_internal);

-- Promotion-topic squadron scope (V85 column).
CREATE INDEX IF NOT EXISTS idx_promotion_topic_owning_squadron_id ON promotion_topic(owning_squadron_id);

-- User -> squadron link (V81 column). Hot path: SquadronScopeService
-- joins this on every non-admin request to resolve the caller's scope.
CREATE INDEX IF NOT EXISTS idx_app_user_squadron_id ON app_user(squadron_id);

-- JobOrder dual squadron columns (V83). Both columns participate in the
-- cross-staffel workspace filtering and the "orders by my squadron" /
-- "orders for my squadron" badges in the orders list.
CREATE INDEX IF NOT EXISTS idx_job_order_creating_squadron_id   ON job_order(creating_squadron_id);
CREATE INDEX IF NOT EXISTS idx_job_order_requesting_squadron_id ON job_order(requesting_squadron_id);
