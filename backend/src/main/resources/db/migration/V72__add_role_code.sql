-- Stable, machine-readable identifier for roles. The display name (`name`) can be
-- renamed by an admin without changing the role's identity. `code` is what the
-- DataInitializer matches against on startup so a renamed role is no longer
-- silently re-created with default permissions on the next boot.
ALTER TABLE role ADD COLUMN code VARCHAR(64);

-- Backfill from the well-known names used by DataInitializer until now.
UPDATE role SET code = 'SQUADRON_MEMBER' WHERE name = 'Squadron Member';
UPDATE role SET code = 'OFFICER'         WHERE name = 'Officer';
UPDATE role SET code = 'ADMIN'           WHERE name = 'Admin';
UPDATE role SET code = 'GUEST'           WHERE name = 'Guest';

-- Any role that pre-existed under a different name gets a derived code so the
-- NOT NULL constraint can be added safely; admins can adjust later via SQL.
UPDATE role SET code = UPPER(REPLACE(REPLACE(name, ' ', '_'), '-', '_'))
 WHERE code IS NULL;

ALTER TABLE role ALTER COLUMN code SET NOT NULL;
ALTER TABLE role ADD CONSTRAINT uk_role_code UNIQUE (code);
