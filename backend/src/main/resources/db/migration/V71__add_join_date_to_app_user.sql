-- =====================================================================
-- V71 – Add join_date to app_user
-- Fügt das Beitrittsdatum eines Mitglieds zur Staffel hinzu.
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN join_date DATE;
