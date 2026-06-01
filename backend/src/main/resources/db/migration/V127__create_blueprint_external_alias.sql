-- =====================================================================
-- V127 - Personal Blueprints feature (#327 Phase 1): blueprint_external_alias
-- =====================================================================
-- Why: the SCMDB log-watcher export carries only a blueprint productName
-- string (no id), while the local master list (table `blueprint`, synced
-- from the SC Wiki) keys products by output_name. Names drift between the
-- two sources, so the personal-blueprint import resolves each SCMDB name to
-- a product and, when an admin/user resolves an unmatched name manually,
-- remembers that decision here so future imports auto-match. This mirrors
-- the material_external_alias cross-reference pattern (V108).
--
-- The alias points at a product by its normalized `product_key` (+ a
-- `product_name` snapshot and the optional resolved `output_item_id`),
-- rather than at a single recipe row, because ownership is per product.
--
-- The unique constraint on (source_system, external_name) makes an external
-- name resolve deterministically; a duplicate add returns HTTP 409.
--
-- Rollback: DROP TABLE blueprint_external_alias. Leaf catalogue, no inbound FKs.

CREATE TABLE blueprint_external_alias (
    id              UUID PRIMARY KEY,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    source_system   VARCHAR(32)              NOT NULL,
    external_name   VARCHAR(255)             NOT NULL,
    product_key     VARCHAR(255)             NOT NULL,
    product_name    VARCHAR(255)             NOT NULL,
    output_item_id  UUID                     REFERENCES game_item(id) ON DELETE SET NULL,
    note            TEXT,
    created_by      VARCHAR(255),
    CONSTRAINT chk_blueprint_external_alias_source_system
        CHECK (source_system IN ('SCMDB')),
    CONSTRAINT uk_blueprint_external_alias_source_external_name
        UNIQUE (source_system, external_name)
);

CREATE INDEX idx_blueprint_external_alias_product_key
    ON blueprint_external_alias (product_key);

COMMENT ON TABLE blueprint_external_alias IS
    'Curated cross-reference mapping an external catalogue blueprint name (SCMDB) onto a local product (#327). Consulted by the personal-blueprint import as the alias-resolution step.';
COMMENT ON COLUMN blueprint_external_alias.external_name IS
    'Blueprint product name as it appears in the external catalogue (SCMDB export productName).';
COMMENT ON COLUMN blueprint_external_alias.product_key IS
    'Normalized product key this external name resolves to (matches personal_blueprint.product_key).';
COMMENT ON COLUMN blueprint_external_alias.created_by IS
    'Alias creator: "system" for seeded rows, the JWT sub for user/admin-created rows.';
