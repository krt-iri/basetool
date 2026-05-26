-- =====================================================================
-- V98 - Spezialkommando R1 step 2: org_unit_membership + backfill
-- =====================================================================
-- Why: SPEZIALKOMMANDO_PLAN.md §3.2 introduces a per-user-per-org-unit
-- membership table to model the user's "I am in one Staffel and N SKs"
-- relationship. A user may belong to at most one Staffel (enforced by a
-- partial unique index) and to any number of Spezialkommandos. The
-- `is_logistician` / `is_mission_manager` flags previously stored at the
-- user level on app_user become per-membership in the new shape (per D3
-- "fully scoped roles"); R2 will switch readers/writers; R3 drops the
-- legacy columns. This migration only creates the new structure and
-- copies today's data into it.
--
-- The `kind` column is denormalized from `org_unit.kind` so the partial
-- unique index "at most one Staffel membership per user" can be
-- expressed as a Postgres partial unique index without crossing tables.
-- A BEFORE INSERT/UPDATE trigger keeps the value in sync with the
-- referenced org_unit; org_unit.kind is in practice immutable (no
-- application path mutates it) so the trigger is a safety net rather
-- than a hot code path.
--
-- The `is_lead` flag is the per-SK admin lever (D2): a Lead of SK X may
-- add/remove members of X without holding global ADMIN. The CHECK
-- constraint enforces that is_lead is never TRUE on a Staffel
-- membership (Staffeln already use the global ADMIN/OFFICER roles for
-- member administration).
--
-- Backfill rule: every app_user with a non-null squadron_id gets a
-- Staffel membership of `kind = 'SQUADRON'`, inheriting the user's
-- current `is_logistician` and `is_mission_manager` flags. Users with
-- a NULL squadron_id (admins, guests, brand-new accounts) do not get a
-- membership row — matching today's behaviour where they default into
-- "all squadrons" or "no scope" via the application-layer admin check.
--
-- Idempotency: trigger function uses CREATE OR REPLACE, table / index /
-- constraints all use IF NOT EXISTS, backfill INSERT is guarded with
-- ON CONFLICT DO NOTHING against the composite PK.
--
-- Rollback: drop the trigger, drop the table; app_user.squadron_id and
-- its flag columns are still authoritative for the entire R1 release
-- cycle.

-- ---------------------------------------------------------------------
-- 1. Create the membership table
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS org_unit_membership (
    user_id            UUID        NOT NULL,
    org_unit_id        UUID        NOT NULL,
    kind               VARCHAR(32) NOT NULL,
    is_logistician     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_mission_manager BOOLEAN     NOT NULL DEFAULT FALSE,
    is_lead            BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, org_unit_id),
    CONSTRAINT fk_org_unit_membership_user
        FOREIGN KEY (user_id)     REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_org_unit_membership_org_unit
        FOREIGN KEY (org_unit_id) REFERENCES org_unit(id) ON DELETE CASCADE,
    CONSTRAINT chk_org_unit_membership_kind
        CHECK (kind IN ('SQUADRON', 'SPECIAL_COMMAND')),
    CONSTRAINT chk_org_unit_membership_lead_only_on_special_command
        CHECK (is_lead = FALSE OR kind = 'SPECIAL_COMMAND')
);

-- "At most one Staffel membership per user." Postgres partial unique
-- indexes can't reference another table, hence the denormalized `kind`
-- column kept in sync by the trigger below.
CREATE UNIQUE INDEX IF NOT EXISTS uq_org_unit_membership_one_squadron
    ON org_unit_membership (user_id)
    WHERE kind = 'SQUADRON';

-- Reverse-lookup index: "list members of org unit X" hits this every
-- time the SK admin page / member roster renders.
CREATE INDEX IF NOT EXISTS idx_org_unit_membership_org_unit
    ON org_unit_membership (org_unit_id);

-- ---------------------------------------------------------------------
-- 2. Sync trigger: keep org_unit_membership.kind aligned with org_unit
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION sync_org_unit_membership_kind()
RETURNS TRIGGER AS $$
DECLARE
    resolved_kind VARCHAR(32);
BEGIN
    SELECT kind INTO resolved_kind
      FROM org_unit
     WHERE id = NEW.org_unit_id;

    IF resolved_kind IS NULL THEN
        RAISE EXCEPTION 'org_unit % does not exist (cannot derive kind for membership)', NEW.org_unit_id;
    END IF;

    NEW.kind := resolved_kind;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_org_unit_membership_kind_ins ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_kind_ins
BEFORE INSERT ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION sync_org_unit_membership_kind();

DROP TRIGGER IF EXISTS trg_org_unit_membership_kind_upd ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_kind_upd
BEFORE UPDATE OF org_unit_id ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION sync_org_unit_membership_kind();

-- ---------------------------------------------------------------------
-- 3. Backfill: every non-null squadron_id becomes a Staffel membership
-- ---------------------------------------------------------------------
-- The trigger above will set `kind = 'SQUADRON'` automatically; the
-- explicit value in the INSERT is therefore redundant but kept for
-- readability and to make the data-shape intent obvious to anyone
-- reading the migration in isolation.
INSERT INTO org_unit_membership (
        user_id, org_unit_id, kind, is_logistician, is_mission_manager, is_lead, joined_at, version, created_at, updated_at
)
SELECT u.id,
       u.squadron_id,
       'SQUADRON',
       COALESCE(u.is_logistician, FALSE),
       COALESCE(u.is_mission_manager, FALSE),
       FALSE,
       COALESCE(u.join_date::timestamptz, NOW()),
       0,
       NOW(),
       NOW()
  FROM app_user u
 WHERE u.squadron_id IS NOT NULL
ON CONFLICT (user_id, org_unit_id) DO NOTHING;
