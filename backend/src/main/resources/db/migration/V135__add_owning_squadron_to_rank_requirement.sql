-- =============================================================================
-- V135 - Squadron scope on rank_requirement (own owning_squadron_id column)
-- =============================================================================
-- Why: the promotion subsystem is squadron-scoped — every Staffel that has the
-- promotion feature enabled runs its own catalog (topics, categories, level
-- contents, rank requirements) and its own member evaluations. Topics carry the
-- squadron FK (V85) and the rest of the tree normally inherits the scope through
-- the topic reference (Plan §3.2 "no denormalisation"). rank_requirement is the
-- one exception: both its topic_id and category_id are nullable, so a "global"
-- requirement (neither set) — which is global WITHIN a single Staffel, i.e. "any
-- N categories of THIS Staffel must reach the level" — has nothing to derive its
-- owner from. To keep every rank requirement anchored to exactly one Staffel it
-- gets its own owning_squadron_id, stamped from the creator's active squadron.
--
-- Mirrors promotion_topic.owning_squadron_id verbatim: the column keeps the
-- legacy `owning_squadron_id` name (readability + constraint-name continuity),
-- the FK targets org_unit(id) (the squadron table was dropped in V105), and a
-- V101-style BEFORE INSERT/UPDATE trigger refuses any owner whose org_unit.kind
-- is not 'SQUADRON' so promotion data can never be owned by a Spezialkommando
-- (Plan §3.3). The Java field RankRequirement.owningSquadron stays typed
-- Squadron, guarded by the ArchUnit rule rankRequirementOwningSquadronMustStay
-- TypedSquadronNotOrgUnit.

ALTER TABLE rank_requirement ADD COLUMN owning_squadron_id UUID;

-- Backfill in three passes, most-specific first. A topic-scoped requirement
-- takes the topic's owner; a category-scoped one the category's topic's owner;
-- any remaining (truly global, pre-existing) row defaults to the canonical
-- IRIDIUM Staffel — the only Staffel that existed before the multi-squadron
-- rollout, so legacy global rules were implicitly IRIDIUM's.
UPDATE rank_requirement r
   SET owning_squadron_id = t.owning_squadron_id
  FROM promotion_topic t
 WHERE r.topic_id = t.id
   AND r.owning_squadron_id IS NULL;

UPDATE rank_requirement r
   SET owning_squadron_id = t.owning_squadron_id
  FROM promotion_category c
  JOIN promotion_topic t ON c.topic_id = t.id
 WHERE r.category_id = c.id
   AND r.owning_squadron_id IS NULL;

UPDATE rank_requirement
   SET owning_squadron_id = '00000000-0000-0000-0000-000000000001'
 WHERE owning_squadron_id IS NULL;

ALTER TABLE rank_requirement ALTER COLUMN owning_squadron_id SET NOT NULL;

ALTER TABLE rank_requirement
    ADD CONSTRAINT fk_rank_requirement_owning_squadron
    FOREIGN KEY (owning_squadron_id) REFERENCES org_unit(id);

-- List/eligibility queries filter on owning_squadron_id on every promotion read;
-- the planner needs the index from day one or those endpoints fall back to a
-- sequential scan of the whole table.
CREATE INDEX idx_rank_requirement_owning_squadron
    ON rank_requirement (owning_squadron_id);

-- ---------------------------------------------------------------------------
-- kind='SQUADRON' guard — mirror of V101's guard_promotion_topic_owner_kind.
-- Defence-in-depth on top of the typed Squadron field + ArchUnit rule: a direct
-- INSERT/UPDATE issued by ad-hoc SQL or a mis-typed entity refactor could place
-- an SK UUID in owning_squadron_id without tripping the org_unit FK; this
-- trigger rejects it.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION guard_rank_requirement_owner_kind()
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
            'rank_requirement.owning_squadron_id % does not resolve to any org_unit row',
            NEW.owning_squadron_id;
    END IF;

    IF owner_kind <> 'SQUADRON' THEN
        RAISE EXCEPTION
            'rank_requirement.owning_squadron_id % references an org_unit of kind % - '
            'promotion data may only be owned by Squadron rows '
            '(SPEZIALKOMMANDO_PLAN.md §3.3)',
            NEW.owning_squadron_id, owner_kind;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_guard_rank_requirement_owner_kind ON rank_requirement;

CREATE TRIGGER trg_guard_rank_requirement_owner_kind
    BEFORE INSERT OR UPDATE OF owning_squadron_id ON rank_requirement
    FOR EACH ROW
    EXECUTE FUNCTION guard_rank_requirement_owner_kind();

COMMENT ON FUNCTION guard_rank_requirement_owner_kind() IS
    'SPEZIALKOMMANDO_PLAN.md §3.3 guard: refuse INSERT/UPDATE of '
    'rank_requirement.owning_squadron_id pointing at a non-SQUADRON org_unit. '
    'Defence-in-depth on top of the Java-side typed Squadron field and ArchUnit.';
