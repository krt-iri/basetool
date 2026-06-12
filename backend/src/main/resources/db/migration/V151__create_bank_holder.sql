-- =====================================================================
-- V151 - Kartell bank (epic #556, Phase 1): bank_holder
-- =====================================================================
-- Why: aUEC physically exists only on Star Citizen player accounts, so every
-- bank account tracks WHICH player holds WHICH part of its balance
-- (REQ-BANK-003). This is the bank-local holder registry: one row per player
-- acting as custodian, created by bank staff via the user lookup. The row
-- carries an optional FK to app_user plus a denormalized handle snapshot -
-- the ledger must survive user deletion, so the FK is ON DELETE SET NULL and
-- the handle stays readable. Holders are never hard-deleted (bank_posting
-- references them with ON DELETE RESTRICT, V153); deactivation only blocks
-- new postings.
--
-- UNIQUE (user_id) keeps one holder row per linked user; PostgreSQL treats
-- NULLs as distinct, so multiple orphaned snapshot rows (deleted users) are
-- allowed.
--
-- Rollback: DROP TABLE bank_holder (after V153, which references it).

CREATE TABLE bank_holder (
    id         UUID PRIMARY KEY,
    version    BIGINT                   NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    user_id    UUID                     REFERENCES app_user (id) ON DELETE SET NULL,
    handle     VARCHAR(255)             NOT NULL,
    active     BOOLEAN                  NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_bank_holder_user UNIQUE (user_id)
);

COMMENT ON TABLE bank_holder IS
    'Bank-local registry of players physically holding org money (epic #556, REQ-BANK-003). Survives user deletion via the handle snapshot.';
COMMENT ON COLUMN bank_holder.user_id IS
    'Optional link to the basetool user; SET NULL on user deletion - the handle snapshot keeps the ledger readable.';
COMMENT ON COLUMN bank_holder.handle IS
    'Denormalized player handle captured at registration time; displayed everywhere the holder appears.';
COMMENT ON COLUMN bank_holder.active IS
    'FALSE blocks new postings naming this holder; existing ledger history stays untouched.';
