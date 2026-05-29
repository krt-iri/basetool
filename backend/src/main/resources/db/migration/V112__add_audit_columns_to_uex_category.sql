-- =====================================================================
-- V112 - SC Wiki sync R2: add audit timestamp columns to uex_category
-- =====================================================================
-- Why: V109 (R1) created the {@code uex_category} reference table but
-- left {@code created_at} / {@code updated_at} off the column list.
-- {@code UexCategory} extends {@code AbstractEntity<Integer>} which
-- inherits {@code @CreationTimestamp createdAt} and {@code @UpdateTimestamp
-- updatedAt} — Hibernate's {@code ddl-auto: validate} now refuses to
-- start with a "missing column [created_at]" error. R2 adds them here
-- instead of mutating V109 in place (Flyway migrations are immutable
-- once shipped; R1 already merged carries V109).
--
-- Rollback: DROP the two columns. Additive only.

ALTER TABLE uex_category
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
