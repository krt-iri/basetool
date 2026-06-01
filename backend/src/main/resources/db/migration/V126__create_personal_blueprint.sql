-- =====================================================================
-- V126 - Personal Blueprints feature (#327 Phase 1): personal_blueprint
-- =====================================================================
-- Why: lets every authenticated user record which crafting blueprints
-- they have already unlocked in-game. Ownership is per PRODUCT (the
-- blueprint's output item), NOT per recipe: several SC Wiki blueprint
-- recipes can share one product name, and the SCMDB import only knows the
-- product name, so a single owned row stands for "I own the blueprint for
-- product X". Identity is the normalized `product_key` derived from the
-- SC Wiki output_name; `product_name` keeps the original display spelling;
-- `output_item_id` optionally links to the resolved game_item for later
-- cross-feature use (it is informational here, not the identity).
--
-- Owner is the Keycloak `sub` claim; every non-admin query MUST filter by
-- owner_sub (multi-user data isolation rule). Optimistic locking via the
-- standard `version` column.
--
-- Rollback: DROP TABLE personal_blueprint. No other table references it.

CREATE TABLE personal_blueprint (
    id              UUID PRIMARY KEY,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    owner_sub       VARCHAR(64)              NOT NULL,
    product_key     VARCHAR(255)             NOT NULL,
    product_name    VARCHAR(255)             NOT NULL,
    output_item_id  UUID                     REFERENCES game_item(id) ON DELETE SET NULL,
    acquired_at     TIMESTAMP WITH TIME ZONE,
    note            VARCHAR(2000),
    CONSTRAINT uk_personal_blueprint_owner_product
        UNIQUE (owner_sub, product_key)
);

CREATE INDEX idx_personal_blueprint_owner
    ON personal_blueprint (owner_sub);

CREATE INDEX idx_personal_blueprint_output_item
    ON personal_blueprint (output_item_id);

COMMENT ON TABLE personal_blueprint IS
    'Per-user record of unlocked crafting blueprints (#327). Ownership is per product (output item), keyed by normalized product_key. Owner is the Keycloak sub claim.';
COMMENT ON COLUMN personal_blueprint.owner_sub IS
    'Keycloak JWT sub of the owning user. All non-admin queries MUST filter by this column.';
COMMENT ON COLUMN personal_blueprint.product_key IS
    'Normalized SC Wiki output_name (lowercased, collapsed whitespace, normalized punctuation). Product-level identity; unique per owner.';
COMMENT ON COLUMN personal_blueprint.product_name IS
    'Original display spelling of the product at the time of save.';
COMMENT ON COLUMN personal_blueprint.output_item_id IS
    'Optional FK to the resolved game_item (the produced item). Informational link for later cross-feature use; not the identity.';
COMMENT ON COLUMN personal_blueprint.acquired_at IS
    'Optional in-game acquisition time; pre-filled from the SCMDB ts on import.';
