-- =====================================================================
-- V113 - SC Wiki sync R3: external_sync_report audit table
-- =====================================================================
-- Why: SC_WIKI_SYNC_PLAN.md §8.8 introduces a single append-only audit
-- table that captures every interesting finding of a sync cycle (UEX or
-- SC Wiki). R3 is the first writer — the Wiki commodity sync emits
-- SKIP_JUNK / CREATED_WIKI_ONLY / LOOKS_LIKE_ITEM / LINKED_VIA_ALIAS /
-- MULTI_MATCH_AMBIGUOUS rows so an admin can review what the merge did
-- on the /admin/sync-reports pages.
--
-- The table is an event log, not an aggregate: no @Version / optimistic
-- locking (rows are insert-only), and `ran_at` is the timestamp. Each
-- sync cycle stamps a shared `run_id` so the admin UI can group events
-- by run. Retention is enforced in the application layer
-- (SyncReportService keeps the last 30 runs per source) rather than via
-- a DB job — the volume is tiny (a few hundred rows per run at most).
--
-- source_system carries a CHECK (small fixed set). event_type is left
-- unconstrained on purpose: the enum grows every phase (R4 adds
-- UNRESOLVED_INGREDIENT / WIKI_MISSING, R7 adds price events), and a
-- CHECK would force a migration each time for no integrity gain — the
-- JPA @Enumerated(STRING) mapping is the source of truth.
--
-- Rollback: DROP TABLE external_sync_report. Leaf table, no FKs in or
-- out.

CREATE TABLE IF NOT EXISTS external_sync_report (
    id             UUID PRIMARY KEY,
    run_id         UUID                     NOT NULL,
    ran_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    source_system  VARCHAR(16)              NOT NULL,
    event_type     VARCHAR(64)              NOT NULL,
    aggregate      VARCHAR(64)              NOT NULL,
    external_uuid  UUID,
    external_id    INTEGER,
    external_name  VARCHAR(255),
    detail         TEXT,
    CONSTRAINT chk_external_sync_report_source_system
        CHECK (source_system IN ('UEX', 'SCWIKI'))
);

CREATE INDEX IF NOT EXISTS idx_external_sync_report_run
    ON external_sync_report(run_id);

-- DESC on ran_at: the admin page lists most-recent-first, and the
-- retention sweep finds the newest 30 run_ids per source.
CREATE INDEX IF NOT EXISTS idx_external_sync_report_source
    ON external_sync_report(source_system, ran_at DESC);
