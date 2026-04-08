-- Activate the pg_trgm extension if not exists
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add GIN indexes for ILIKE / LOWER(LIKE) queries to improve performance
CREATE INDEX IF NOT EXISTS idx_mission_name_trgm ON mission USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_mission_description_trgm ON mission USING gin (description gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_material_name_trgm ON material USING gin (name gin_trgm_ops);
