-- =====================================================================
-- V178 - app_user: Discord per-guild server nickname (epic #720, REQ-DATA-008)
-- =====================================================================
-- Why: an admin deciding a Discord registration needs to recognise the person
-- by the name they carry inside the das-kartell guild (the server-specific
-- "nick"), which differs from the global Discord username. The Keycloak Discord
-- IdP captures it best-effort at each login from the guild-member call and
-- carries it in the discord_guild_nickname token claim; the backend persists it
-- here for display in the approval queue. NULLable: no nickname set, no Discord
-- login, or the optional capture mappers are not configured. Display-only -- it
-- grants nothing and is surfaced on the admin-only queue, never in a shared DTO.
-- O(1) metadata add (no backfill, no index: never queried, only read per-row).

ALTER TABLE app_user
    ADD COLUMN discord_guild_nickname VARCHAR(255);

COMMENT ON COLUMN app_user.discord_guild_nickname IS
    'Discord per-guild server nickname captured best-effort at Discord login via the Keycloak IdP guild-member mapper claim; shown to admins in the approval queue. NULL when unset/non-Discord/mappers absent. Epic #720 / REQ-DATA-008.';
