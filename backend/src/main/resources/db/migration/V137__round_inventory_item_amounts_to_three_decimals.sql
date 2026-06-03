-- Re-round every inventory amount to three decimals (HALF_UP / commercial rounding).
-- V59 already did this once, but the refinery store-into-inventory path
-- (RefineryOrderService) summed the `amount` double without rounding and re-introduced
-- >3-decimal floating-point artefacts. Observed in production: 37.160000000000004,
-- 2.9299999999999997, 1.5899999999999999 — all surfaced raw in the book-out dialog.
-- The new @PrePersist/@PreUpdate guard on the InventoryItem entity stops recurrence on
-- every write path; this one-off backfill cleans the rows already stored dirty.
--
-- Idempotent: the float8 comparison in the WHERE only touches rows whose stored double
-- does not already round-trip at <=3 decimals, so re-running on a partially-applied DB
-- changes nothing. Casting the rounded numeric back into the double precision column
-- yields the canonical nearest double (the one whose shortest decimal form is <=3 places).
UPDATE inventory_item
SET amount = ROUND(amount::numeric, 3)
WHERE amount <> ROUND(amount::numeric, 3)::double precision;
