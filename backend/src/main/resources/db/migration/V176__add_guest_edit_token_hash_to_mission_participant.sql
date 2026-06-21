-- =====================================================================
-- V176 - mission_participant: per-row guest edit-token hash (security audit M1)
-- =====================================================================
-- Why: the slim participant mutate/delete/check-in/out/payout endpoints are
-- permitAll and were gated only by "the row has no linked user" — so ANY
-- anonymous caller who saw a public mission's roster could edit or delete
-- another person's guest sign-up (cross-actor vandalism / payout tampering).
-- We bind each anonymous guest sign-up to its creator with an unguessable,
-- server-issued capability token: the plaintext is returned once at create time
-- (kept client-side), and only its SHA-256 hash is persisted here. A later guest
-- write must present the matching token (or be a mission manager). The hash is
-- never returned and is not a secret-at-rest beyond what a stolen DB already
-- exposes.
--
-- NULLable: registered (user-linked) participants never carry a token, and guest
-- rows created before this migration have none — those legacy guest rows become
-- editable only by a mission manager/officer/admin (fail-closed, the safe default).
-- 64 chars = lowercase hex SHA-256. O(1) metadata add (no backfill).

ALTER TABLE mission_participant
    ADD COLUMN guest_edit_token_hash VARCHAR(64);

COMMENT ON COLUMN mission_participant.guest_edit_token_hash IS
    'SHA-256 hex of the per-row capability token minted for an anonymous guest sign-up; required (or a mission-manager role) to mutate/delete the guest row. NULL for user-linked participants and pre-V176 guest rows. Security audit M1 / REQ-SEC-018.';
