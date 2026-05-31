-- Job orders can now carry an optional free-text comment supplied at creation time (context /
-- delivery notes). Plain text, length-capped to mirror JobOrder.COMMENT_MAX_LENGTH and the DTO
-- @Size bound; rendered HTML-escaped in the UI. Additive, nullable -- O(1) metadata-only ADD.
ALTER TABLE job_order ADD COLUMN comment VARCHAR(1000);
