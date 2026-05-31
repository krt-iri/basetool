-- =====================================================================
-- V123 - Item Job Orders: schema foundation (issue #304, Phase 1 / #306)
-- =====================================================================
-- Introduces a second kind of Job Order ("item order") alongside the
-- existing material order. This migration is purely additive: it adds a
-- discriminator column to job_order (backfilled to MATERIAL for every
-- existing row, so behaviour is unchanged) and the new child tables that
-- hold the ordered items, their snapshotted material requirements, and
-- the item-handover fulfilment trail.
--
-- Scope notes (see issue #304):
--   * Job Order is a cross-staffel workspace: these tables are reached
--     only via the parent job_order and carry no owning_org_unit column
--     and no org-unit access filter.
--   * required_quantity is a plain double; the unit (SCU vs Stueck) is
--     interpreted from the linked material's quantity_type, never stored
--     here.
--   * blueprint / game_item references are stable (the SC-Wiki sync
--     upserts, never deletes) so plain RESTRICT FKs are safe.

-- ---------------------------------------------------------------------
-- 1. Order-kind discriminator on job_order
-- ---------------------------------------------------------------------
ALTER TABLE job_order
    ADD COLUMN type VARCHAR(16) NOT NULL DEFAULT 'MATERIAL';

ALTER TABLE job_order
    ADD CONSTRAINT chk_job_order_type CHECK (type IN ('MATERIAL', 'ITEM'));

-- ---------------------------------------------------------------------
-- 2. Ordered item lines
-- ---------------------------------------------------------------------
CREATE TABLE job_order_item (
    id               UUID PRIMARY KEY,
    job_order_id     UUID NOT NULL,
    game_item_id     UUID NOT NULL,
    blueprint_id     UUID NOT NULL,
    amount           INTEGER NOT NULL,
    delivered_amount INTEGER NOT NULL DEFAULT 0,
    parent_item_id   UUID,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    version          BIGINT NOT NULL,
    CONSTRAINT chk_job_order_item_amount CHECK (amount > 0),
    CONSTRAINT chk_job_order_item_delivered CHECK (delivered_amount >= 0),
    CONSTRAINT fk_job_order_item_job_order
        FOREIGN KEY (job_order_id) REFERENCES job_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_order_item_game_item
        FOREIGN KEY (game_item_id) REFERENCES game_item (id),
    CONSTRAINT fk_job_order_item_blueprint
        FOREIGN KEY (blueprint_id) REFERENCES blueprint (id),
    CONSTRAINT fk_job_order_item_parent
        FOREIGN KEY (parent_item_id) REFERENCES job_order_item (id) ON DELETE SET NULL
);

CREATE INDEX idx_job_order_item_job_order ON job_order_item (job_order_id);
CREATE INDEX idx_job_order_item_game_item ON job_order_item (game_item_id);
CREATE INDEX idx_job_order_item_blueprint ON job_order_item (blueprint_id);
CREATE INDEX idx_job_order_item_parent ON job_order_item (parent_item_id);

-- ---------------------------------------------------------------------
-- 3. Snapshotted material requirements per ordered item line
-- ---------------------------------------------------------------------
CREATE TABLE job_order_item_material (
    id                  UUID PRIMARY KEY,
    job_order_item_id   UUID NOT NULL,
    material_id         UUID NOT NULL,
    required_quantity   DOUBLE PRECISION NOT NULL,
    quality_requirement VARCHAR(8) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    version             BIGINT NOT NULL,
    CONSTRAINT chk_job_order_item_material_quality
        CHECK (quality_requirement IN ('GOOD', 'NONE')),
    CONSTRAINT fk_job_order_item_material_item
        FOREIGN KEY (job_order_item_id) REFERENCES job_order_item (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_order_item_material_material
        FOREIGN KEY (material_id) REFERENCES material (id)
);

CREATE INDEX idx_job_order_item_material_item ON job_order_item_material (job_order_item_id);
CREATE INDEX idx_job_order_item_material_material ON job_order_item_material (material_id);

-- ---------------------------------------------------------------------
-- 4. Item-handover fulfilment trail (mirrors job_order_handover audit)
-- ---------------------------------------------------------------------
CREATE TABLE job_order_item_handover (
    id                    UUID PRIMARY KEY,
    job_order_id          UUID NOT NULL,
    handover_time         TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_handle      VARCHAR(255) NOT NULL,
    executing_user_id     UUID,
    executing_squadron_id UUID,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT NOT NULL,
    CONSTRAINT fk_job_order_item_handover_job_order
        FOREIGN KEY (job_order_id) REFERENCES job_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_order_item_handover_executing_user
        FOREIGN KEY (executing_user_id) REFERENCES app_user (id) ON DELETE SET NULL,
    -- Squadron extends OrgUnit (@DiscriminatorValue 'SQUADRON'); the legacy squadron table was
    -- dropped in V105, so the executing-squadron audit FK resolves via org_unit (kind='SQUADRON'),
    -- mirroring the V105 retarget of job_order_handover.executing_squadron_id.
    CONSTRAINT fk_job_order_item_handover_executing_squadron
        FOREIGN KEY (executing_squadron_id) REFERENCES org_unit (id)
);

CREATE INDEX idx_job_order_item_handover_job_order ON job_order_item_handover (job_order_id);
CREATE INDEX idx_job_order_item_handover_executing_user ON job_order_item_handover (executing_user_id);
CREATE INDEX idx_job_order_item_handover_executing_squadron ON job_order_item_handover (executing_squadron_id);

CREATE TABLE job_order_item_handover_entry (
    id                         UUID PRIMARY KEY,
    job_order_item_handover_id UUID NOT NULL,
    job_order_item_id          UUID NOT NULL,
    amount                     INTEGER NOT NULL,
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    version                    BIGINT NOT NULL,
    CONSTRAINT chk_job_order_item_handover_entry_amount CHECK (amount > 0),
    CONSTRAINT fk_job_order_item_handover_entry_handover
        FOREIGN KEY (job_order_item_handover_id) REFERENCES job_order_item_handover (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_order_item_handover_entry_item
        FOREIGN KEY (job_order_item_id) REFERENCES job_order_item (id)
);

CREATE INDEX idx_job_order_item_handover_entry_handover ON job_order_item_handover_entry (job_order_item_handover_id);
CREATE INDEX idx_job_order_item_handover_entry_item ON job_order_item_handover_entry (job_order_item_id);
