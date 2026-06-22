# Activity audit logs — Lager, Aufträge, Raffinerie, Mein Inventar

> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-22.

Area: `AUDIT` · Related: [`bank.md`](bank.md) (the bank's own audit trail, REQ-BANK-012),
[`observability.md`](observability.md) (the log-stream PII rule), [ADR-0037](../adr/0037-shared-multi-domain-activity-audit-log.md).

## Context

The Kartell bank already keeps an immutable, admin-only audit trail (REQ-BANK-012). The same
guarantee is extended to four more areas — **Lagerverwaltung** (`InventoryItem`),
**Auftragsverwaltung** (`JobOrder`), **Raffinerieverwaltung** (`RefineryOrder`) and **Mein
Inventar** (`PersonalInventoryItem`). Every activity in each area is captured into a separate,
admin-only log; all five logs (the four here plus the bank's) are read on one page with a tab
switcher, and each can be exported as a PDF for a chosen period.

The four new areas share **one** physical table (`audit_event`) with a `domain` discriminator; the
bank keeps its own `bank_audit_event` table (it has bank-specific reference columns and shipped
first). The storage choice and the unified-viewer architecture are recorded in
[ADR-0037](../adr/0037-shared-multi-domain-activity-audit-log.md).

---

### REQ-AUDIT-001 — Immutable, complete, admin-only activity audit log

Every state-mutating activity in the four areas writes exactly **one** row to an **insert-only**
audit table (`audit_event`, modeled after `bank_audit_event` — no `@Version`, no updates) **in the
same transaction as the business write**. An audit-insert failure rolls the mutation back, so the
trail has **no silent gaps**. Each event stores: timestamp (UTC), the acting user's id (FK `ON
DELETE SET NULL`) **plus** a denormalized actor-handle snapshot (the trail must survive user
deletion), the `domain`, the event type, the affected subject's id + a denormalized subject-label
snapshot, an optional target-user reference, and a compact details payload.

Coverage is **complete**, including the cross-area writers and the system/automatic mutations:

- **Lager** — create / edit / note / book-out (consume, transfer, sell) / delivery-toggle /
  bulk-checkout / global wipe; plus the cross-area writers (refinery store → `INVENTORY_RECEIVED_FROM_REFINERY`,
  job-order handover → `INVENTORY_HANDED_OVER`), the org-unit re-stamp on membership change, and the
  owner-reassignment on user deletion.
- **Aufträge** — create (material/item) / edit / status / priority / delete / completion (a single
  funnel — manual and auto-completion via handover both record exactly one `JOB_ORDER_COMPLETED`) /
  reassign / assignee add/remove/note / material+inventory unlink / material+item handover / claim
  upsert+withdraw.
- **Raffinerie** — order create / update / cancel / store; refining-method reference CRUD; the
  scheduled UEX method+yield sync (one summary event per run, actor `system`); owner-reassignment on
  user deletion.
- **Mein Inventar** — create / update / delete (admin-on-behalf carries the target user).

The audit table is **business data, not logging** — the [`observability.md`](observability.md) rule
(never write names, emails or tokens to the **log stream**) is unaffected and still applies. User
**free text** (inventory/assignee notes, handover recipient handles) is **never** written into the
details payload — only ids, counts and lengths (the actor handle and non-personal subject labels
such as a material name or order title are snapshotted, exactly as the bank trail snapshots holder
handles).

The log is readable **only by admins**: the `/api/v1/audit/**` URL matcher requires
`hasRole('ADMIN')`, the controller carries a matching method-level `@PreAuthorize`, and the
`/admin/audit-log` page is admin-gated. Audit rows are never exposed through any non-admin endpoint.

Reference columns are plain UUIDs (no FKs) so audit rows **outlive** every referenced aggregate
(job orders are hard-deleted, inventory rows are depleted) without delete-ordering constraints.

**Acceptance**

- [ ] For a representative mutation in each area a test asserts exactly one matching audit event
  (domain, type, actor, subject).
- [ ] Audit write failures fail the business transaction (same TX — no silent gaps); the optimistic-
  locking landmine paths (book-out, handover, store, delete, completion, claim) record without a 409.
- [ ] Non-admin access to `/api/v1/audit/**` and `/admin/audit-log`: 403; the sidebar link is hidden.

**Enforced by:** `AuditServiceTest`, `AuditQueryIntegrationTest`, `AuditAdminControllerSecurityTest`,
per-domain emission assertions in the service tests · **Code:** `service/AuditService`,
`model/AuditEvent`, `model/AuditDomain`, `model/AuditEventType`, `controller/AuditAdminController`,
`db/migration/V179` · **Decision:** [ADR-0037](../adr/0037-shared-multi-domain-activity-audit-log.md)

### REQ-AUDIT-002 — Unified admin audit viewer

All five logs are read on **one** admin page (`/admin/audit-log`) with a **five-way tab switcher**
(Bank · Lager · Aufträge · Raffinerie · Mein Inventar) built from the design-system `.tab-nav`
component. The bank tab reads the existing `/api/v1/bank/admin/audit` endpoint; the four area tabs
read `/api/v1/audit/{domain}`; both DTO shapes are adapted into one uniform row view so a single
template renders every tab. Each tab is paginated and filterable by **period** (the
`datetime-split-group` picker), **actor** and **event type** (the per-area type list). Filtering and
paging swap **in place** (`krtFetch`, the epic #571 pattern); the legacy `/admin/bank-audit` URL
redirects here with the bank tab preselected.

**Acceptance**

- [ ] An admin sees five tabs; switching a tab loads that area's log; filtering/paging stays in place.
- [ ] `/admin/bank-audit` redirects to `/admin/audit-log?domain=BANK`.

**Enforced by:** `AdminAuditLogPageControllerTest`, `AuditLogE2eTest` · **Code:**
`controller/AdminAuditLogPageController`, `templates/admin/audit-log.html`, `static/js/audit-log.js`

### REQ-AUDIT-003 — Per-area period export (PDF + JSON)

For each tab, an admin can export that log for a **user-selected period** (from/to) in two formats:

- **PDF** — generated backend-side with OpenPDF through the shared `KrtPdfSupport` /
  `AuditLogPdfFormat` layer, following the KRT design system (dark page background, KRT orange
  `#E77E23`, **embedded Lato**, A4, footer + logo) exactly like the bank statement/report PDFs. The
  document prints the **raw, language-neutral event code** (the on-screen viewer shows the localized
  label) so the trail stays unambiguous and the backend bundle is not duplicated. The
  `X-User-Time-Zone` header localizes the document timestamps.
- **JSON** — the period's events as the same DTOs the viewer consumes, delivered as a downloadable
  `*.json` attachment (UTC instants verbatim, no time-zone header).

Both are admin-gated and delivered via the established `ResponseEntity<…>` + frontend-proxy +
fetch/blob download pattern (no native dialogs; period validation + failures render inline). Each
export is itself audit-logged (`*_AUDIT_EXPORTED`, and the bank's `AUDIT_LOG_EXPORTED`), with the
chosen `format=pdf|json` in the details.

**Acceptance**

- [ ] Each area's PDF export renders the period's events, pinned by a `PdfTextExtractor` test, and
  the JSON export returns the period's DTOs; both write one matching `*_AUDIT_EXPORTED` audit event.
- [ ] An inverted period is rejected (400); both export endpoints are admin-gated.

**Enforced by:** `AuditReportServiceTest`, `BankAuditReportServiceTest`, `AuditAdminControllerSecurityTest`
· **Code:** `service/AuditReportService`, `service/BankAuditReportService`,
`service/pdf/AuditLogPdfFormat`, `controller/AuditReportProxyController`
