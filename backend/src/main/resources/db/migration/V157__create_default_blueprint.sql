-- =====================================================================
-- V157 - Default blueprints (REQ-INV-016/017): default_blueprint
-- =====================================================================
-- Why: a small set of crafting blueprints is unlocked by default on every
-- Star Citizen account (e.g. S-38 Pistol, P4-AR Rifle, the Field Recon Suit
-- pieces, the starter magazines). They can never be earned in-game again and
-- therefore never appear in an SCMDB / Basetool Blueprint Extractor export, so
-- the import never materialises them - the basetool has to grant them to every
-- user itself (REQ-INV-016). This table is the admin-curated source of truth
-- for "which products count as default" (REQ-INV-017); personal_blueprint rows
-- are materialised from it for every user and kept non-removable.
--
-- product_key is the SAME normalized identity used by personal_blueprint
-- (BlueprintNameNormalizer), so a granted default lines up with the catalog,
-- the product search and the availability/coverage views. UNIQUE(product_key)
-- makes the admin "add default" idempotent and lets the per-user grant use
-- ON CONFLICT (owner_sub, product_key) DO NOTHING.
--
-- output_item_id is an informational FK to the resolved game_item, ON DELETE
-- SET NULL so a game_item churn during a catalog re-sync only nulls the link
-- and never blocks the materialising INSERT into personal_blueprint (whose
-- own output_item_id FK would otherwise reject a dangling id). scwiki_key is an
-- optional audit trail of the representative recipe the default resolved to;
-- created_by stores the admin sub that added it, or "system" for the seed.
--
-- Rollback: DROP TABLE default_blueprint. No other table references it.

CREATE TABLE default_blueprint (
    id             UUID PRIMARY KEY,
    version        BIGINT                   NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    product_key    VARCHAR(255)             NOT NULL,
    product_name   VARCHAR(255)             NOT NULL,
    output_item_id UUID                     REFERENCES game_item(id) ON DELETE SET NULL,
    scwiki_key     VARCHAR(255),
    created_by     VARCHAR(64),
    CONSTRAINT uk_default_blueprint_product_key UNIQUE (product_key)
);

-- The product search / cleanup joins land on output_item_id; index it like
-- personal_blueprint.output_item_id (V126).
CREATE INDEX idx_default_blueprint_output_item
    ON default_blueprint (output_item_id);

COMMENT ON TABLE default_blueprint IS
    'Admin-curated set of blueprint products unlocked by default on every account (REQ-INV-016/017). Materialised into personal_blueprint for every user and kept non-removable.';
COMMENT ON COLUMN default_blueprint.product_key IS
    'Normalized product identity (BlueprintNameNormalizer); matches personal_blueprint.product_key. UNIQUE so the admin add and per-user grant are idempotent.';
COMMENT ON COLUMN default_blueprint.product_name IS
    'Display spelling of the default product at the time it was added.';
COMMENT ON COLUMN default_blueprint.output_item_id IS
    'Optional FK to the resolved produced game_item; informational, ON DELETE SET NULL so a catalog re-sync never blocks the materialising grant.';
COMMENT ON COLUMN default_blueprint.scwiki_key IS
    'Optional representative SC Wiki recipe key captured when the default was resolved against the catalog; audit aid only.';
COMMENT ON COLUMN default_blueprint.created_by IS
    'Keycloak sub of the admin who added the default, or "system" for the initial seed.';
