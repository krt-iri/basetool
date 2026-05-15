-- ============================================================================
-- V74: Remove rank_requirement rows that describe multi-step promotions.
-- ============================================================================
-- The promotion model only supports single-step rank transitions
-- (from_rank - to_rank = 1, e.g. 20 -> 19). Multi-step rows like 20 -> 18 are
-- now rejected at the service boundary; this migration prunes any historical
-- multi-step rows so the new validation is consistent with the data on disk.

DELETE FROM rank_requirement
WHERE (from_rank - to_rank) <> 1;
