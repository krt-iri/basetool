-- =====================================================================
-- V150 - Kartell bank (epic #556, Phase 1): bank_account
-- =====================================================================
-- Why: the bank manages accounts for every organizational layer (REQ-BANK-001):
-- ORG_UNIT (one per Staffel/Spezialkommando), AREA (free-form Bereich name),
-- CARTEL and CARTEL_BANK (singletons) and SPECIAL (dynamically named). There is
-- deliberately NO per-player account type - players appear only as holders
-- (custody dimension, V151/V153). Accounts are mutable aggregates (rename,
-- close/reopen) with the standard optimistic-locking columns; balances are
-- never stored here - they are computed from bank_posting (ADR-0010).
--
-- Account numbers are human-readable, server-generated and never reused
-- (`KB-0042`); the sequence backs the generator. Singleton and per-org-unit
-- uniqueness are partial unique indexes, not application convention
-- (REQ-BANK-001 acceptance).
--
-- Rollback: DROP TABLE bank_account; DROP SEQUENCE bank_account_no_seq.
-- (V152/V153 reference this table and must be rolled back first.)

CREATE SEQUENCE bank_account_no_seq START WITH 1;

CREATE TABLE bank_account (
    id          UUID PRIMARY KEY,
    version     BIGINT                   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    account_no  VARCHAR(16)              NOT NULL,
    name        VARCHAR(255)             NOT NULL,
    type        VARCHAR(16)              NOT NULL,
    status      VARCHAR(16)              NOT NULL DEFAULT 'ACTIVE',
    org_unit_id UUID                     REFERENCES org_unit (id),
    area_name   VARCHAR(255),
    CONSTRAINT uq_bank_account_account_no UNIQUE (account_no),
    CONSTRAINT chk_bank_account_type
        CHECK (type IN ('ORG_UNIT', 'AREA', 'CARTEL', 'CARTEL_BANK', 'SPECIAL')),
    CONSTRAINT chk_bank_account_status
        CHECK (status IN ('ACTIVE', 'CLOSED')),
    -- Owner reference must match the account type (REQ-BANK-001): ORG_UNIT
    -- carries the org_unit FK, AREA carries the free-form area name, all other
    -- types carry neither.
    CONSTRAINT chk_bank_account_owner_ref
        CHECK (
            (type = 'ORG_UNIT' AND org_unit_id IS NOT NULL AND area_name IS NULL)
            OR (type = 'AREA' AND org_unit_id IS NULL AND area_name IS NOT NULL)
            OR (type IN ('CARTEL', 'CARTEL_BANK', 'SPECIAL')
                AND org_unit_id IS NULL AND area_name IS NULL)
        )
);

-- At most one account per org unit (REQ-BANK-001). Partial: only ORG_UNIT rows
-- carry the FK.
CREATE UNIQUE INDEX uq_bank_account_org_unit
    ON bank_account (org_unit_id)
    WHERE org_unit_id IS NOT NULL;

-- The CARTEL and CARTEL_BANK accounts are singletons: a constant-expression
-- partial unique index allows at most one row per type.
CREATE UNIQUE INDEX uq_bank_account_singleton_cartel
    ON bank_account ((1))
    WHERE type = 'CARTEL';

CREATE UNIQUE INDEX uq_bank_account_singleton_cartel_bank
    ON bank_account ((1))
    WHERE type = 'CARTEL_BANK';

COMMENT ON TABLE bank_account IS
    'Kartell bank accounts (epic #556, REQ-BANK-001). One row per org-unit/area/cartel/cartel-bank/special account; balances are computed from bank_posting, never stored.';
COMMENT ON COLUMN bank_account.account_no IS
    'Human-readable, server-generated, never-reused account number (KB-<zero-padded sequence>).';
COMMENT ON COLUMN bank_account.org_unit_id IS
    'Owner reference for type ORG_UNIT (Staffel or Spezialkommando); NULL for all other types.';
COMMENT ON COLUMN bank_account.area_name IS
    'Free-form Bereich name for type AREA (Bereiche are not entities, see docs/specs/org-chart.md); NULL for all other types.';
