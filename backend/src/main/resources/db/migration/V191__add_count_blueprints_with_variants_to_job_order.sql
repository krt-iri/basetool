-- =====================================================================
-- V191 - Item orders: per-order blueprint-coverage variant counting toggle
--        (issue #822, REQ-ORDERS-021)
-- =====================================================================
-- Why: the item-order blueprint-coverage view always counted cosmetic
-- variants of an ordered item toward availability (family-key matching via
-- BlueprintVariantFamilyResolver). When an order requests one specific
-- variant that is misleading: members owning *other* variants of the family
-- are counted even though they cannot supply the requested one. This adds a
-- per-order switch so the coverage can be counted with variants (the historic
-- behaviour) or by exact-name match.
--
-- Purely additive and behaviour-preserving: the column is backfilled to TRUE
-- on every existing row, so existing item orders keep counting variants.
-- Relevant only to ITEM orders; ignored for MATERIAL orders.
-- ---------------------------------------------------------------------

ALTER TABLE job_order
    ADD COLUMN count_blueprints_with_variants BOOLEAN NOT NULL DEFAULT TRUE;
