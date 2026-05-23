-- =============================================================================
-- SPEZIALKOMMANDO_PLAN.md §3.3 + R4 — DB-level guard against an SK being
-- referenced as a promotion-topic owner.
--
-- Plan §3.3 keeps `promotion_topic.owning_squadron_id` typed against squadron
-- (not org_unit) precisely so SKs cannot own promotion data — the application
-- layer enforces this by typing `PromotionTopic.owningSquadron` as Squadron and
-- ArchUnit rule §8.2 (`promotionTopicOwningSquadronMustStayTypedSquadronNotOrgUnit`)
-- prevents a careless type-loosening. But once V97 made `squadron` a one-way
-- mirror of `org_unit` (R2.b) the column resolves against any org_unit row's
-- primary key — a direct INSERT/UPDATE issued by a future ad-hoc SQL or by a
-- mis-typed entity refactor could place an SK UUID there without tripping any
-- FK. This trigger closes that gap.
--
-- Logic: on every INSERT / UPDATE of promotion_topic.owning_squadron_id, look
-- up the referenced row's kind in org_unit. If the kind is not 'SQUADRON',
-- raise an exception. NULL owners are allowed (the column is nullable today;
-- a future migration can tighten that separately).
--
-- Idempotent: the trigger function is created via CREATE OR REPLACE and the
-- trigger itself is dropped before re-creation. Rollback drops both objects.
-- =============================================================================

CREATE OR REPLACE FUNCTION guard_promotion_topic_owner_kind()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    owner_kind VARCHAR(32);
BEGIN
    IF NEW.owning_squadron_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT kind INTO owner_kind FROM org_unit WHERE id = NEW.owning_squadron_id;

    IF owner_kind IS NULL THEN
        RAISE EXCEPTION
            'promotion_topic.owning_squadron_id % does not resolve to any org_unit row',
            NEW.owning_squadron_id;
    END IF;

    IF owner_kind <> 'SQUADRON' THEN
        RAISE EXCEPTION
            'promotion_topic.owning_squadron_id % references an org_unit of kind % - '
            'promotion data may only be owned by Squadron rows '
            '(SPEZIALKOMMANDO_PLAN.md §3.3)',
            NEW.owning_squadron_id, owner_kind;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_promotion_topic_owner_kind ON promotion_topic;

CREATE TRIGGER trg_guard_promotion_topic_owner_kind
    BEFORE INSERT OR UPDATE OF owning_squadron_id ON promotion_topic
    FOR EACH ROW
    EXECUTE FUNCTION guard_promotion_topic_owner_kind();

COMMENT ON FUNCTION guard_promotion_topic_owner_kind() IS
    'SPEZIALKOMMANDO_PLAN.md §3.3 guard: refuse INSERT/UPDATE of '
    'promotion_topic.owning_squadron_id pointing at a non-SQUADRON org_unit. '
    'Defence-in-depth on top of the Java-side typed Squadron field and ArchUnit §8.2.';
