-- =====================================================================
-- V175 - Missing performance indexes (REQ-DATA-006)
-- =====================================================================
-- Round-three FK / hot-query index backfill (after V34, V92, V122). Each
-- index below covers a predicate that currently falls back to a sequential
-- scan because the only existing index leads with a different column, or
-- because no index covers the column at all. None of these change the
-- schema shape that Hibernate `ddl-auto=validate` checks (tables/columns
-- stay identical); they only add access paths. All are `IF NOT EXISTS` so
-- the migration is idempotent on a partially-patched database.

-- (1) job_order_assignees.user_id — the only index touching this column is the
-- composite UNIQUE (job_order_id, user_id) from V147, which leads with
-- job_order_id and therefore cannot serve a user_id-only predicate. Powers the
-- ON DELETE behaviour of the assignee edge on user deletion and the
-- `findByAssigneeId` reverse lookup.
CREATE INDEX IF NOT EXISTS idx_job_order_assignees_user_id
    ON job_order_assignees (user_id);

-- (2) bank_posting.holder_id — V153 only indexes (account_id, holder_id), which
-- leads with account_id, so the holder-distribution / `holderTotal` aggregate
-- sequentially scans the append-only (unbounded-growth) ledger. A standalone
-- holder_id index keeps that bounded as the ledger grows.
CREATE INDEX IF NOT EXISTS idx_bank_posting_holder_id
    ON bank_posting (holder_id);

-- (3) bank_transaction.initiated_by — FK to app_user with no covering index;
-- the ON DELETE SET NULL on user deletion has to scan the transaction table.
CREATE INDEX IF NOT EXISTS idx_bank_transaction_initiated_by
    ON bank_transaction (initiated_by);

-- (4) app_user.approved_by_id — FK to app_user (the approving admin) with no
-- covering index; keeps approver back-references and user-deletion integrity
-- checks off a full table scan.
CREATE INDEX IF NOT EXISTS idx_app_user_approved_by_id
    ON app_user (approved_by_id);

-- (5) app_user pending-approval queue — partial index matching
-- `findByApprovalStatusOrderByCreatedAtAsc(PENDING)`: the WHERE clause prunes
-- the index to just the (normally tiny) pending set and the created_at key
-- serves the oldest-first ORDER BY without a sort.
CREATE INDEX IF NOT EXISTS idx_app_user_pending_approval
    ON app_user (created_at)
    WHERE approval_status = 'PENDING';

-- (6) job_order active board — partial index matching
-- `findAllActiveWithMaterials` (status IN ('OPEN','IN_PROGRESS')
-- ORDER BY priority ASC NULLS LAST, display_id DESC). As terminal orders
-- accumulate, the unfiltered scan + sort grows without bound; the partial
-- index keeps the active set's lookup and ordering constant. Mirrors the
-- partial-index strategy of V155's notification index.
CREATE INDEX IF NOT EXISTS idx_job_order_active_priority
    ON job_order (priority ASC NULLS LAST, display_id DESC)
    WHERE status IN ('OPEN', 'IN_PROGRESS');
