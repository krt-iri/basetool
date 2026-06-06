-- =====================================================================
-- V142 - User-level default payout preference
-- =====================================================================
-- Why: a member's per-mission payout choice (mission_participant.payout_preference,
-- added in V42) defaults to PAYOUT for everyone. Members who always donate (or always
-- cash out) had to flip it by hand in every single mission. This adds a personal
-- default on the user that pre-fills the participant's preference at sign-up
-- (MissionService#addParticipant); the per-mission value stays independently editable
-- and is NOT rewritten when the default later changes. REQ-MISSION-002 / issue #469.
--
-- Nullable on purpose: NULL = "no explicit choice", which sign-up treats as PAYOUT, so
-- behaviour is unchanged for every existing user until they opt in. Reuses the
-- PayoutPreference enum (PAYOUT / DONATE), stored as its name() exactly like the
-- participant column.
--
-- Rollback: ALTER TABLE app_user DROP COLUMN default_payout_preference. Additive only --
-- no existing row data is touched.

ALTER TABLE app_user ADD COLUMN default_payout_preference VARCHAR(50);
