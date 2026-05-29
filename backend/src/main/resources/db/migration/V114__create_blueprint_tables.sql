-- =====================================================================
-- V114 - SC Wiki sync R4: blueprint recipe graph
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §6.3.2-6.3.4 introduces the crafting-blueprint
-- aggregate (1559 recipes as of 4.8.0). R4's ScWikiBlueprintSyncService
-- populates it from /api/blueprints. A blueprint has an output GameItem,
-- an ordered list of ingredients (each a RESOURCE → material, or an
-- ITEM → game_item), and a list of RESOURCE-only dismantle returns.
--
-- DEVIATION from the plan's §6.3.3 CHECK constraints: the plan declares
--   CHECK (kind='RESOURCE' AND material_id IS NOT NULL AND game_item_id IS NULL ...)
-- i.e. it forces the matching FK NOT NULL. But §8.2 explicitly wants an
-- *unresolved* ingredient to still persist (with its wiki_resource_uuid /
-- wiki_name_snapshot) so a later sync re-resolves it after an admin adds
-- an alias — that row has a NULL FK. The two requirements contradict.
-- R4 keeps the forensic-persistence behaviour (§8.2) and relaxes the
-- CHECK to enforce only the meaningful invariants:
--   * kind/FK exclusivity — a RESOURCE never carries a game_item_id, an
--     ITEM never carries a material_id (the matching FK MAY be NULL while
--     unresolved);
--   * quantity-type exclusivity — a RESOURCE uses quantity_scu, an ITEM
--     uses quantity_units, never the other.
--
-- Concurrency: the blueprint owns its ingredient / dismantle-return rows
-- (cascade ALL + orphanRemoval). The sync mutates the managed collections
-- and relies on dirty-checking — it never runs a @Modifying bulk update
-- inside the per-blueprint loop, so the CLAUDE.md detach-clear trap does
-- not apply.
--
-- Rollback: DROP the three tables in FK order (returns/ingredients first,
-- then blueprint). All additive; no existing table is touched.

CREATE TABLE IF NOT EXISTS blueprint (
    id                       UUID PRIMARY KEY,
    version                  BIGINT                   NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    scwiki_uuid              UUID                     NOT NULL,
    scwiki_key               VARCHAR(255),
    output_item_id           UUID REFERENCES game_item(id),
    output_name              VARCHAR(255),
    category_uuid            UUID,
    craft_time_seconds       INTEGER,
    is_available_by_default  BOOLEAN                  NOT NULL DEFAULT FALSE,
    ingredient_count         INTEGER,
    unlocking_missions_count INTEGER,
    game_version_seen        VARCHAR(64),
    scwiki_synced_at         TIMESTAMP WITH TIME ZONE,
    scwiki_deleted_at        TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_blueprint_scwiki_uuid UNIQUE (scwiki_uuid)
);

CREATE INDEX IF NOT EXISTS idx_blueprint_output_item ON blueprint(output_item_id);

CREATE TABLE IF NOT EXISTS blueprint_ingredient (
    id                  UUID PRIMARY KEY,
    version             BIGINT                   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    blueprint_id        UUID                     NOT NULL REFERENCES blueprint(id),
    order_index         INTEGER                  NOT NULL,
    kind                VARCHAR(16)              NOT NULL,
    material_id         UUID REFERENCES material(id),
    game_item_id        UUID REFERENCES game_item(id),
    wiki_resource_uuid  UUID,
    wiki_item_uuid      UUID,
    wiki_name_snapshot  VARCHAR(255),
    quantity_scu        DOUBLE PRECISION,
    quantity_units      INTEGER,
    CONSTRAINT chk_blueprint_ingredient_kind
        CHECK (kind IN ('RESOURCE', 'ITEM')),
    CONSTRAINT chk_blueprint_ingredient_fk_exclusivity
        CHECK ((kind = 'RESOURCE' AND game_item_id IS NULL)
            OR (kind = 'ITEM' AND material_id IS NULL)),
    CONSTRAINT chk_blueprint_ingredient_quantity_exclusivity
        CHECK ((kind = 'RESOURCE' AND quantity_units IS NULL)
            OR (kind = 'ITEM' AND quantity_scu IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_blueprint_ingredient_blueprint
    ON blueprint_ingredient(blueprint_id);
CREATE INDEX IF NOT EXISTS idx_blueprint_ingredient_material
    ON blueprint_ingredient(material_id);
CREATE INDEX IF NOT EXISTS idx_blueprint_ingredient_game_item
    ON blueprint_ingredient(game_item_id);

CREATE TABLE IF NOT EXISTS blueprint_dismantle_return (
    id                  UUID PRIMARY KEY,
    version             BIGINT                   NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    blueprint_id        UUID                     NOT NULL REFERENCES blueprint(id),
    order_index         INTEGER                  NOT NULL,
    material_id         UUID REFERENCES material(id),
    wiki_resource_uuid  UUID,
    wiki_name_snapshot  VARCHAR(255),
    quantity_scu        DOUBLE PRECISION
);

CREATE INDEX IF NOT EXISTS idx_blueprint_dismantle_return_blueprint
    ON blueprint_dismantle_return(blueprint_id);
CREATE INDEX IF NOT EXISTS idx_blueprint_dismantle_return_material
    ON blueprint_dismantle_return(material_id);
