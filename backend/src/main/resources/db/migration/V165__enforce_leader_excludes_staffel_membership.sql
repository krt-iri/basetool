-- =====================================================================
-- V165 - Org hierarchy R2 (epic #692, REQ-ORG-017): a leader holds no
--        Staffel membership (cross-row invariant).
-- =====================================================================
-- Why: REQ-ORG-017 requires that anyone holding a leadership role — an
-- SK-Leiter (is_lead), a Bereichsleitung member (is_bereichsleiter /
-- is_bereichskoordinator / is_bereichsoperator) or an OL member
-- (is_ol_member) — belongs to NO Staffel. This is a cross-row rule (it
-- relates a user's leadership-flag membership to their Staffel
-- membership on a different row), which a single-row CHECK cannot
-- express, so it is enforced by a trigger. The service layer adds a
-- matching guard for clean 4xx responses (defence in depth); this
-- trigger is the DB backstop that no raw-SQL / batch path can bypass.
--
-- Direction is symmetric:
--   * a row that carries any leadership flag => the user must hold no
--     SQUADRON membership;
--   * a SQUADRON membership row => the user must hold no
--     leadership-flagged membership.
--
-- Fires BEFORE INSERT (new membership) and BEFORE UPDATE (a flag flipped
-- on an existing membership). The trigger name sorts after
-- sync_org_unit_membership_kind (k < l), so NEW.kind is resolved on
-- INSERT before this check reads it; on a flag-only UPDATE the kind
-- column is unchanged and already correct.
--
-- A flag-LESS BEREICH/OL membership row (should one exist) is
-- unaffected: it carries no leadership flag and is not a SQUADRON row,
-- so neither branch fires. (The SK-Leiter's Bereichsleitung seat is
-- derived, computed from is_lead + the SK's parent Bereich, NOT a stored
-- membership row, so it never reaches this trigger at all.)
--
-- Idempotency: CREATE OR REPLACE FUNCTION + DROP TRIGGER IF EXISTS.
-- Rollback: drop the triggers and the function.

CREATE OR REPLACE FUNCTION enforce_leader_excludes_squadron()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_lead OR NEW.is_bereichsleiter OR NEW.is_bereichskoordinator
       OR NEW.is_bereichsoperator OR NEW.is_ol_member THEN
        IF EXISTS (
            SELECT 1 FROM org_unit_membership
             WHERE user_id = NEW.user_id
               AND kind = 'SQUADRON'
        ) THEN
            RAISE EXCEPTION
                'user % holds a leadership role (SK-Lead/Bereichsleitung/OL) and may not belong to a Staffel',
                NEW.user_id;
        END IF;
    ELSIF NEW.kind = 'SQUADRON' THEN
        IF EXISTS (
            SELECT 1 FROM org_unit_membership
             WHERE user_id = NEW.user_id
               AND org_unit_id <> NEW.org_unit_id
               AND (is_lead OR is_bereichsleiter OR is_bereichskoordinator
                    OR is_bereichsoperator OR is_ol_member)
        ) THEN
            RAISE EXCEPTION
                'user % belongs to a Staffel and may not hold a leadership role (SK-Lead/Bereichsleitung/OL)',
                NEW.user_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_org_unit_membership_leader_excl_squadron_ins ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_leader_excl_squadron_ins
BEFORE INSERT ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION enforce_leader_excludes_squadron();

DROP TRIGGER IF EXISTS trg_org_unit_membership_leader_excl_squadron_upd ON org_unit_membership;
CREATE TRIGGER trg_org_unit_membership_leader_excl_squadron_upd
BEFORE UPDATE ON org_unit_membership
FOR EACH ROW EXECUTE FUNCTION enforce_leader_excludes_squadron();
