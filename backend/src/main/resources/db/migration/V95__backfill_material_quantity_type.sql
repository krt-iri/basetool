-- Backfill NULL material.quantity_type to 'SCU' and tighten the column so the
-- value can never be NULL again.
--
-- Why: V44 added quantity_type as nullable VARCHAR(20) DEFAULT 'SCU', but
-- Hibernate's INSERT statements always include the column with the Java field
-- value (null for new instances created by UexCommodityService), so the DB
-- DEFAULT never fired for UEX-imported materials. Every commodity ever pulled
-- from UEX ended up with quantity_type = NULL.
--
-- The store dialog in the refinery-order page treats NULL as "not SCU" and
-- skips the units->SCU conversion (100 units == 1 SCU), so a 2.21 SCU output
-- got booked as 221 SCU into inventory. See issue #230.
--
-- The fix is three-fold: (1) backfill NULL -> 'SCU' here so existing data is
-- correct; (2) NOT NULL + DEFAULT here so a future code path cannot reintroduce
-- the null; (3) the matching JPA entity gains nullable=false and initialises
-- the field to QuantityType.SCU so new instances from the UEX sync ship 'SCU'
-- explicitly. The controller still defends against NULL defensively in case
-- the migration order ever changes.

UPDATE material SET quantity_type = 'SCU' WHERE quantity_type IS NULL;

ALTER TABLE material ALTER COLUMN quantity_type SET DEFAULT 'SCU';
ALTER TABLE material ALTER COLUMN quantity_type SET NOT NULL;
