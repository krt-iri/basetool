-- =============================================================================
-- Privacy / data minimisation (GDPR): first and last name are no longer collected, transmitted
-- or stored. The application stopped reading/writing app_user.first_name / app_user.last_name in
-- this same release (the columns were removed from the User JPA entity); the Keycloak token
-- mappers for given_name / family_name / name are disabled operationally so the PII is not even
-- sent anymore.
--
-- This is PHASE 1 of a two-phase removal (db/migration/README.md "Destructive operations"):
--   * Phase 1 (this file): scrub the existing values to NULL right now so no name PII remains in
--     the database, while keeping the now-unused columns in place for one release. ddl-auto =
--     validate tolerates unmapped extra columns, so the app still boots after the entity dropped
--     the fields.
--   * Phase 2 (next release): V134__drop_user_first_last_name.sql will run the actual
--     ALTER TABLE app_user DROP COLUMN first_name / last_name.
-- The grace period keeps an app-deployment rollback safe (an older build still expecting the
-- columns can boot because they still exist).
--
-- Idempotent: re-running on a partially-applied database leaves the result unchanged.
-- =============================================================================

UPDATE app_user
   SET first_name = NULL,
       last_name = NULL
 WHERE first_name IS NOT NULL
    OR last_name IS NOT NULL;
