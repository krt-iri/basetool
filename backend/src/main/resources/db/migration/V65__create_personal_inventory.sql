-- Personal Inventory feature: every authenticated user owns exactly one personal inventory
-- containing N personal inventory items. The location refers to a UEX City or Space Station
-- by its UEX numeric id; the display name is denormalized into location_name_snapshot for
-- offline rendering. Optimistic locking is provided via the standard `version` column.

CREATE TABLE personal_inventory_item (
    id                       UUID PRIMARY KEY,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    owner_sub                VARCHAR(64)  NOT NULL,
    name                     VARCHAR(120) NOT NULL,
    note                     VARCHAR(2000),
    location_uex_id          INTEGER      NOT NULL,
    location_type            VARCHAR(20)  NOT NULL,
    location_name_snapshot   VARCHAR(255) NOT NULL,
    quantity                 INTEGER      NOT NULL,
    CONSTRAINT ck_personal_inventory_item_location_type
        CHECK (location_type IN ('CITY', 'SPACE_STATION')),
    CONSTRAINT ck_personal_inventory_item_quantity_positive
        CHECK (quantity >= 1)
);

CREATE INDEX idx_personal_inventory_item_owner
    ON personal_inventory_item (owner_sub);

CREATE INDEX idx_personal_inventory_item_owner_name
    ON personal_inventory_item (owner_sub, name);

COMMENT ON TABLE personal_inventory_item IS
    'Per-user personal inventory entries (Personal Inventory feature). Owner is identified by the Keycloak sub claim.';
COMMENT ON COLUMN personal_inventory_item.owner_sub IS
    'Keycloak JWT sub of the owning user. All non-admin queries MUST filter by this column.';
COMMENT ON COLUMN personal_inventory_item.location_uex_id IS
    'UEX numeric id (id_city or id_space_station) of the referenced location.';
COMMENT ON COLUMN personal_inventory_item.location_type IS
    'CITY or SPACE_STATION - discriminator for location_uex_id.';
COMMENT ON COLUMN personal_inventory_item.location_name_snapshot IS
    'Denormalized display name of the location at the time of save; used for offline display.';
