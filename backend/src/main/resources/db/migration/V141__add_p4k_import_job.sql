-- =====================================================================
-- V141 - KRT P4K Reader: asynchronous catalog-import jobs
-- =====================================================================
-- Why: the P4K catalog import (parse ~60k DataForge records + reconcile
-- them against game_item / ship_type / manufacturer / material /
-- blueprint) takes far longer than a snappy request/response cycle. Run
-- synchronously it blocked the admin's browser tab and tripped the
-- frontend's read-timeout / the reverse-proxy timeout. This migration
-- backs a fire-and-forget job model instead: the upload enqueues a job
-- and returns immediately; an @Async worker parses + reconciles in the
-- background and writes the per-type result back onto the job row, which
-- the admin page polls. A finished PREVIEW job can then be APPLIED (a
-- second background job) from the stored upload without re-uploading.
--
--   p4k_import_job          — one async run (PREVIEW or APPLY). Carries
--                             the lifecycle status, the seed-new opt-in,
--                             the serialized P4kImportResultDto once it
--                             succeeds (result_json, plain JSON text — it
--                             is only ever stored and echoed back, never
--                             queried into), and an error message on
--                             failure. preview_job_id links an APPLY job
--                             back to the PREVIEW it was launched from
--                             (informational; the APPLY copies the upload
--                             into its own payload row so it stays
--                             self-contained). Extends AbstractEntity, so
--                             it carries version / created_at / updated_at.
--   p4k_import_job_payload  — the uploaded catalog bytes, split off into a
--                             1:1 side table keyed by job_id so listing /
--                             polling the jobs never drags the multi-MB
--                             upload through the persistence context. ON
--                             DELETE CASCADE so pruning a job drops its
--                             payload with it.
--
-- Rollback: DROP TABLE p4k_import_job_payload, p4k_import_job. Additive
-- only — no existing row data is touched.

CREATE TABLE p4k_import_job (
    id              UUID                     PRIMARY KEY,
    kind            VARCHAR(16)              NOT NULL,
    status          VARCHAR(16)              NOT NULL,
    seed_new        BOOLEAN                  NOT NULL DEFAULT FALSE,
    source_filename VARCHAR(255),
    file_size_bytes BIGINT,
    result_json     TEXT,
    error_message   TEXT,
    preview_job_id  UUID,
    created_by      UUID                     NOT NULL,
    started_at      TIMESTAMP WITH TIME ZONE,
    finished_at     TIMESTAMP WITH TIME ZONE,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT chk_p4k_import_job_kind
        CHECK (kind IN ('PREVIEW', 'APPLY')),
    CONSTRAINT chk_p4k_import_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_p4k_import_job_preview
        FOREIGN KEY (preview_job_id) REFERENCES p4k_import_job (id) ON DELETE SET NULL
);

-- The admin job list is ordered newest-first; the prune sweep deletes by age.
CREATE INDEX idx_p4k_import_job_created_at ON p4k_import_job (created_at DESC);

CREATE TABLE p4k_import_job_payload (
    job_id  UUID  PRIMARY KEY REFERENCES p4k_import_job (id) ON DELETE CASCADE,
    content BYTEA NOT NULL
);
