-- =====================================================================
-- V172 - app_user: Discord identity link (epic #720, Track 1, REQ-DATA-006)
-- =====================================================================
-- Why: a Discord login federated through Keycloak must be linkable to the local
-- app_user row so the membership gate (T1.2) and the approval flow (T1.3) can
-- recognise a returning Discord user. The Keycloak Discord IdP mapper carries the
-- Discord user id (a numeric snowflake, stored as text) into the token, and the
-- backend persists it into this column at login (auto-link). NULLable: credential
-- -login users have no Discord id, and Postgres treats NULLs as distinct so the
-- UNIQUE constraint never collides across all the non-Discord rows. The UNIQUE
-- constraint creates its own index, so lookups by discord_user_id are covered.
-- O(1) metadata add (no backfill).

ALTER TABLE app_user
    ADD COLUMN discord_user_id VARCHAR(32);

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_discord_user_id UNIQUE (discord_user_id);

COMMENT ON COLUMN app_user.discord_user_id IS
    'Discord user id (snowflake, numeric, stored as text) linked at first Discord login via the Keycloak IdP mapper claim; NULL for non-Discord users. Epic #720 / REQ-DATA-006.';
