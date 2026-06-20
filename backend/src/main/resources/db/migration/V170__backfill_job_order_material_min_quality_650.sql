-- The orders refining-grade "Gut" quality floor moved from 700 to 650 (the in-game refining grade
-- is now 650): see REQ-ORDERS-017 and CreateJobOrderMaterialDto, whose @Min/@Max bound is now 650.
-- Every existing job-order material was stamped with the old fixed floor value 700 -- the only
-- concrete value the create DTO ever accepted (NULL means "Keine"/no floor). Rewrite that stored
-- value to the new floor so the persisted requirement and the order-detail view agree with the 650
-- the matching constants (JobOrderService.GOOD_QUALITY_FLOOR, JobOrderItemService.
-- GOOD_QUALITY_THRESHOLD) now use; otherwise un-edited legacy orders would keep displaying 700.
-- "Keine" (NULL) rows are left untouched. Idempotent: once applied, no row matches min_quality = 700.
UPDATE job_order_material SET min_quality = 650 WHERE min_quality = 700;
