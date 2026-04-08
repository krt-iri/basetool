-- Assign a default owner to refinery orders that might not have one (safety measure)
-- Using the first available user as a fallback.
UPDATE refinery_order 
SET owner_id = (SELECT id FROM app_user ORDER BY created_at LIMIT 1) 
WHERE owner_id IS NULL;
