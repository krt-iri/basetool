CREATE TABLE inventory_item (
    id              UUID PRIMARY KEY,
    version         BIGINT,
    created_at      TIMESTAMP WITH TIME ZONE,
    updated_at      TIMESTAMP WITH TIME ZONE,
    user_id         UUID NOT NULL REFERENCES app_user(id),
    material_id     UUID NOT NULL REFERENCES material(id),
    location_id     UUID NOT NULL REFERENCES location(id),
    quality         INTEGER NOT NULL,
    amount          DOUBLE PRECISION NOT NULL
);
