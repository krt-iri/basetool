> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-12.
> **Owner area:** BANK · **Related ADRs:** ADR-0009, ADR-0010, ADR-0011
> **Status:** Proposed — owner review pending (epic
> [#556](https://github.com/krt-iri/basetool/issues/556)); implementation is
> approval-gated. Each phase PR updates this spec in the same PR (acceptance boxes get
> ticked, `Enforced by` links get filled in).

# Kartell bank — accounts, ledger, grants, audit, exports

## Context & goal

DAS KARTELL runs an in-game bank for its members and units. The basetool gets a dedicated
**bank area** in which a dedicated staff — defined solely by the bank roles, fully
independent of any org-unit membership — manages
accounts for every organizational layer — Staffeln, Spezialkommandos, areas (Bereiche),
the cartel as a whole, the cartel bank itself, and dynamically named special accounts.
There are **no accounts for individual players**. Because aUEC in Star Citizen only
exists on *player* accounts, every bank account additionally tracks its **holder
distribution**: which player physically holds which part of the account's balance (e.g.
the area-Profit account holds 1 000 aUEC — 500 with player greluc, 250 with carol, 250
with doppi). Bank staff book deposits, withdrawals and transfers; every change lands in
an immutable audit log that only administrators can read. Account statements and a management overview are exported
as KRT-design PDFs. The feature is deliberately **independent of seasons, price lines and
the mission/operation profit flows** — it is a standalone ledger.

Work is tracked in epic [#556](https://github.com/krt-iri/basetool/issues/556); the
phase-by-phase implementation plan (including per-phase deployment steps) lives in
[`docs/BANK_PLAN.md`](../BANK_PLAN.md).

## Requirements

### REQ-BANK-001 — Account model & account types

The bank manages **accounts** (`bank_account`). Every account has a unique human-readable
account number (generated, format `KB-<zero-padded sequence>`, e.g. `KB-0042`), a display
name, a type, a status (`ACTIVE` / `CLOSED`) and optimistic-locking metadata. Supported
account types:

|     Type      |                                 Owner reference                                 |       Cardinality        |
|---------------|---------------------------------------------------------------------------------|--------------------------|
| `ORG_UNIT`    | FK → `org_unit` (Staffel **or** Spezialkommando)                                | at most one per org unit |
| `AREA`        | free-form area name (Bereiche are not entities — see `docs/specs/org-chart.md`) | many                     |
| `CARTEL`      | none (the organization as a whole)                                              | singleton                |
| `CARTEL_BANK` | none (the bank's own operating account)                                         | singleton                |
| `SPECIAL`     | free-form name, dynamically created                                             | many                     |

There is deliberately **no per-player account type** — players appear only as *holders*
of organizational money (REQ-BANK-003). Singleton and per-owner uniqueness are enforced
by partial unique indexes, not by application convention alone.

**Acceptance**

- [ ] Creating a second `CARTEL`, `CARTEL_BANK` or per-org-unit account is rejected with
  a 409 and a stable problem code.
- [ ] Account numbers are unique, server-generated and never reused.
- [ ] New entities reference org units via an `org_unit` FK (`org_unit_id`), never
  `squadron_id` (ArchUnit rule
  `noNewJoinColumnReferencingSquadronIdOutsideGrandfatheredEntities`).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-002 — Dynamic account lifecycle, no hard delete

**All** account types — org-unit, area, cartel, cartel-bank and special — are created at
runtime by bank management (and admins) through the same endpoint; nothing is seeded by
migration, including the two singletons. Accounts are thereby **dynamically creatable
and closable** without a migration or restart. Closing an account requires a **zero
balance** (transfer the remainder first); a closed account becomes read-only (no
postings) but stays visible to authorized readers with its full history. Closed accounts
can be reopened by bank management. Accounts are **never hard-deleted**; deactivating an
org unit (soft delete, see `SquadronService.deleteSquadron`) does not touch its bank
account.

**Acceptance**

- [ ] Closing an account with a non-zero balance is rejected (409, stable code).
- [ ] Postings on a `CLOSED` account are rejected; reads and statements still work.
- [ ] Reopen restores full booking capability and is audited.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-003 — Holder distribution: every account is partitioned across players

aUEC physically exists only on Star Citizen **player accounts**, so for every bank
account it must be determinable **which player holds which part** of the account's
balance, and this distribution must be **visible and manageable** in the account and in
the evaluations. The bank therefore maintains a bank-local **holder registry**
(`bank_holder`): one row per player acting as custodian, created by bank staff via the
user lookup, carrying an optional FK to `app_user` plus a denormalized handle snapshot
(the ledger must survive user deletion). **Every posting references exactly one
holder** — a deposit names the player who physically received the money, a withdrawal
the player who paid it out, each transfer leg the player whose stash changes. The
per-(account, holder) sub-balance is the sum of those postings; the sub-balances of an
account always sum exactly to its balance. Moving money between holders **within the
same account** is an intra-account holder rebooking (REQ-BANK-011) — the account balance
is unchanged, only custody moves. Holders are never hard-deleted while postings
reference them.

**Acceptance**

- [ ] A posting without a holder reference is rejected (400) — on every account type.
- [ ] The account detail view, the statement PDF (REQ-BANK-014) and the management
  export (REQ-BANK-015) show the per-holder sub-balances, summing exactly to the
  account balance.
- [ ] A holder row whose linked user is deleted keeps its handle snapshot and its ledger
  history.

**Enforced by:** _pending (Phases 1–3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-004 — Append-only double-entry ledger

All value movements are recorded in an **append-only ledger**: a `bank_transaction`
(type, initiator, note, timestamp) with 1..n `bank_posting` rows (account, signed
amount, holder — REQ-BANK-003). Transaction types: `DEPOSIT` (one positive posting),
`WITHDRAWAL` (one negative posting), `TRANSFER` (two postings summing to zero — covers
account-to-account transfers and intra-account holder rebookings), `WIPE_RESET`
(admin-only, REQ-BANK-013) and `REVERSAL` (corrections). A reversal's
postings are the **negated mirror** of the reversed transaction's postings — its
per-transaction sum is the negation of the original's sum (zero exactly when the
original was a `TRANSFER`). Ledger rows are **never updated or deleted** — mistakes are
corrected by a reversal transaction that references the original. The account balance is
the SQL sum of its postings, computed on read (ADR-0010); per-account running-balance
caching may be added later without changing this contract.

**Acceptance**

- [ ] No service or repository code path issues `UPDATE`/`DELETE` on
  `bank_transaction`/`bank_posting` (test-pinned, mirroring REQ-INV-001 in
  `inventory-lager.md`).
- [ ] `TRANSFER` postings sum to zero per transaction; `REVERSAL` postings are the
  negated mirror of the reversed transaction's postings (service invariant +
  DB-level guard where practical).
- [ ] A reversal stores a FK to the reversed transaction; reversing twice is rejected.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-005 — Whole-aUEC amounts

Bank amounts follow the project-wide whole-number-amounts contract
(`docs/specs/whole-number-amounts.md`, ADR-0002): `BigDecimal` mapped to
`NUMERIC(19,4)`, write DTOs validated with the value-based `@WholeNumber` constraint and
`@DecimalMin("1")` (a zero-amount booking is meaningless), display rounded HALF_UP to
whole aUEC via the frontend `MoneyFormat` bean. Amount inputs use
`<input type="number" step="1" inputmode="numeric">`.

**Acceptance**

- [ ] `500.00` accepted, `500.5` rejected (400) on every booking endpoint.
- [ ] `0` and negative request amounts are rejected (sign is determined by the
  transaction type, not by the caller).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-006 — No overdraft

No posting may take an account balance below zero — and no posting may take any
**(account, holder) sub-balance** below zero either: a withdrawal, transfer leg or
holder rebooking can only move money the named holder actually holds on that account
(REQ-BANK-003). Withdrawals and transfers are validated against the current balances
**inside the booking transaction** with an atomic/locked balance check so concurrent
bookings cannot jointly overdraw an account or a holder stash. Violations surface as 409
with a stable problem code (not 500).

**Acceptance**

- [ ] Concurrent withdrawals that would jointly overdraw an account: exactly one
  succeeds (concurrency test).
- [ ] A booking exceeding the named holder's sub-balance on that account is rejected
  (409), even when the account balance would suffice.
- [ ] The error response names the account and the available balance — without leaking
  data to callers who cannot see the account (403 takes precedence).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-007 — Keycloak roles & hierarchy

Bank access is anchored on two new **Keycloak realm roles** (names proposed, owner may
rename before Phase 1 — see Open questions):

- `Bank Employee` → `ROLE_BANK_EMPLOYEE` — may use the bank area within the limits of
  their per-account grants (REQ-BANK-009).
- `Bank Management` → `ROLE_BANK_MANAGEMENT` (Bankleitung) — sees and manages
  **all** accounts, holders and grants.

Role hierarchy (both `SecurityConfig` beans, kept in sync):
`ROLE_ADMIN > ROLE_BANK_MANAGEMENT > ROLE_BANK_EMPLOYEE`. Admins therefore pass every
bank gate (REQ-BANK-010); bank management passes every employee gate. The roles are
seeded in `DataInitializer` (matched by code `BANK_EMPLOYEE` / `BANK_MANAGEMENT`),
documented in `ROLES_AND_PERMISSIONS.md`, and added to the E2E realm export
(`frontend/src/e2e/resources/realm-export.e2e.json`).

**Acceptance**

- [ ] A user with only `Bank Employee` and zero grants sees an empty bank area — no
  account data, no audit log.
- [ ] `hasRole('BANK_EMPLOYEE')` is satisfied by management and admins via the hierarchy.
- [ ] `ROLES_AND_PERMISSIONS.md` documents the bank column/row in the access matrix.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-008 — Bank membership is independent of org-unit membership

Working in the bank — as employee or management — requires only a basetool account and
the respective Keycloak role (REQ-BANK-007) plus grants (REQ-BANK-009). It is **fully
independent of org-unit membership**: a bank employee or bank manager may, but need
not, also be a member of a Staffel, Spezialkommando or any other org unit. Org-unit
memberships neither qualify nor disqualify anyone for the bank, and joining or leaving
an org unit has **no effect** on bank roles, grants or access. Consequently, bank
authorization decisions **never consult org-unit scope**: `OwnerScopeService` scoping,
contextual `ROLE_X@orgUnitId` authorities and the `X-Active-Org-Unit-Id` admin pin have
no influence on any bank gate — `BankSecurityService` evaluates only the bank roles and
the grant table.

**Acceptance**

- [ ] A user who is a Staffel/SK member **and** holds `Bank Employee` plus a grant can
  use the bank exactly like a membership-less bank employee (matrix test).
- [ ] Joining or leaving an org unit changes nothing about a user's bank access or
  grants (test pins both directions).
- [ ] Bank gates ignore the `X-Active-Org-Unit-Id` pin and contextual org-unit
  authorities (test).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-009 — Per-account grants: view + deposit / withdraw / transfer

Fine-grained bank permissions are **app-managed grants**, not Keycloak roles
(ADR-0011): a `bank_account_grant` row per (user, account) with three independent
capability flags — `can_deposit`, `can_withdraw`, `can_transfer`. The **existence** of a
grant row gives **view access** to that account (a row with all flags false is
view-only). This expresses every required combination: deposit-only, withdraw-only,
both, and rebooking permission. Grants are managed by bank management and admins; every
grant change (create, flag change, revoke) is audited (REQ-BANK-012). Grants can only be
**created** for users holding the `Bank Employee` role; whether the grantee belongs to
an org unit is irrelevant (REQ-BANK-008).

**Acceptance**

- [ ] Capability matrix is enforced server-side per endpoint: deposit needs
  `can_deposit` on the account, withdrawal `can_withdraw`, transfer `can_transfer`
  on the **source** account (REQ-BANK-011 for the destination rule).
- [ ] Grant management endpoints are gated `hasRole('BANK_MANAGEMENT')` (admins pass via
  hierarchy) and reject grantees lacking the employee role.
- [ ] Every grant mutation produces exactly one audit event with before/after flags.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-010 — Visibility matrix

|                                                Actor                                                 |                 Sees                 |                    May change                    |
|------------------------------------------------------------------------------------------------------|--------------------------------------|--------------------------------------------------|
| Everyone without a bank role (anonymous, `GUEST`, members, officers, logisticians, mission managers) | **nothing** (no bank surface at all) | nothing                                          |
| Bank employee (role + grants; org-unit membership irrelevant, REQ-BANK-008)                          | accounts they hold a grant on        | bookings per their capability flags              |
| Bank management (role; org-unit membership irrelevant)                                               | **all** accounts, holders, grants    | all bookings, account lifecycle, holders, grants |
| Admin (`ROLE_ADMIN`)                                                                                 | everything incl. the **audit log**   | everything incl. wipe reset (REQ-BANK-013)       |

The audit log is **admin-only** — bank management does **not** see it. The bank area
contributes nothing to the anonymous/guest surface (consistent with REQ-SEC-009). Bank
endpoints follow the two-gate model (URL matrix outer, `@PreAuthorize` inner):
`/api/v1/bank/admin/**` additionally gets a `hasRole('ADMIN')` URL gate.

**Acceptance**

- [ ] A role/permission matrix test (mirroring `RolePermissionsE2eTest`) proves every
  cell of the table above, including the "nothing" row.
- [ ] The sidebar "Bank" group renders only for `BANK_EMPLOYEE`-or-above; the audit-log
  page only for admins.

**Enforced by:** _pending (Phases 1–4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-011 — Transfer semantics

A transfer (Umbuchung) moves value between two postings of one `TRANSFER` transaction;
each leg names the holder whose stash changes (REQ-BANK-003). Variants, all audited:

1. **Account → account** (e.g. Staffel → SK, area → cartel): employee needs
   `can_transfer` on the **source** account; the **destination** must be an account the
   employee can see (any grant row). Bank management and admins are unrestricted. The
   physical custody may stay with the same player (same holder on both legs) or change
   hands as part of the transfer.
2. **Intra-account holder rebooking** (same account, two different holders): requires
   `can_transfer` on that account; the account balance is unchanged — only custody
   moves between the players.

Self-transfers (same account, same holder on both legs) are rejected.

**Acceptance**

- [ ] An employee with `can_transfer` on A but no grant on B cannot transfer A → B (403).
- [ ] Intra-account holder rebooking changes the holder sub-balances, not the account
  balance.
- [ ] All variants appear in the audit log and the statement PDF with both legs incl.
  their holders.

**Enforced by:** _pending (Phases 1, 3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-012 — Immutable, complete, admin-only audit log

Every bank mutation writes exactly one row to an **insert-only** audit table
(`bank_audit_event`, modeled after `external_sync_report` — no `@Version`, no updates):
bookings of every type, reversals, account lifecycle (create/close/reopen), holder
registry changes, every grant change, the wipe reset, and **PDF exports** (statement and
management export, with parameters). Each event stores: timestamp, actor user id (FK
`ON DELETE SET NULL`) **plus** a denormalized actor handle snapshot (the trail must
survive user deletion), event type, affected account/transaction/target-user references,
and a compact details payload. The audit log is readable **only by admins**
(`hasRole('ADMIN')` on URL **and** method gate), paginated and filterable (period, actor,
account, event type). Audit rows are never exposed through any non-admin endpoint.

The audit table is business data, not logging — the `docs/specs/observability.md` rule
(never log names/emails/tokens to the **log stream**) still applies to bank code.

**Acceptance**

- [ ] For every mutating bank endpoint a test asserts exactly one matching audit event
  (type, actor, references).
- [ ] Audit write failures fail the business transaction (same TX — no silent gaps).
- [ ] Non-admin access to the audit endpoints/pages: 403; the page link is hidden.

**Enforced by:** _pending (Phases 1, 3, 4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-013 — Admin wipe reset

A Star Citizen wipe erases in-game currency. The admin area gets **one button** that
resets **all** account balances to zero: for every account with a non-zero balance the
service books a `WIPE_RESET` transaction (one posting per holder with a non-zero
sub-balance on that account) bringing the balance and every holder sub-balance to
exactly zero. History, statements and audit trail are **preserved** — nothing is
deleted. The action requires `ROLE_ADMIN` and a KRT-styled danger confirmation modal
(no native dialogs) with an explicit consequence text **and a type-to-confirm hurdle**
(the design system's `.confirm-input` pattern, reserved for wipe-reset-grade actions),
and writes one summarizing audit event plus the individual transactions. The operation
is idempotent (a second click on an all-zero bank is a no-op with a notice).

**Acceptance**

- [ ] After the reset every account balance and every holder sub-balance is zero;
  pre-wipe statements still render correctly.
- [ ] The button sits in the admin area, is admin-only, and uses the danger-modal
  pattern with type-to-confirm (`btn-danger` + danger `.krt-modal` + `.confirm-input`),
  matching the A1 mockup (`proposals/bank-admin-varianten.html`).
- [ ] The audit log contains the summary event (actor, account count, total zeroed).

**Enforced by:** _pending (Phases 1, 4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-014 — Account statement PDF (Kontoauszug)

For every account they can see, bank staff can export an **account statement PDF** for a
**user-selected period** (from/to, reusing the `datetime-split-group` filter pattern):
header with account number/name/type/status, opening balance at period start, every
booking in the period (timestamp, type, counter-account where applicable, holder, note,
signed amount, running balance), closing balance, and the closing **holder
distribution** (per-holder sub-balances). The PDF is generated backend-side with OpenPDF, follows
the KRT design system (page background, KRT orange `#E77E23`, **embedded Lato** — the
existing Helvetica-based reports predate the rule), and is delivered via the established
`ResponseEntity<byte[]>` + frontend-proxy + fetch/blob download pattern with the
`X-User-Time-Zone` header. Statement labels come from the backend message bundles
(German default), not string literals. Every export is audited (REQ-BANK-012).

**Acceptance**

- [ ] Opening + sum of period postings = closing balance, pinned by a `PdfTextExtractor`
  test (existing test style).
- [ ] Statement access follows REQ-BANK-010 (employee: granted accounts only).
- [ ] PDF uses embedded Lato and the KRT page background; no PII beyond the account's
  own data.

**Enforced by:** _pending (Phase 3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-015 — Management export: all accounts, last three months (PDF)

Bank management and admins can export a **single PDF over all accounts** covering the
**last three months** (rolling window). Per account: a header line with opening balance
(3 months ago), in/out totals, net change and closing balance, the closing **holder
distribution**, **followed by the itemized bookings of the window** (the owner asked for
the *changes*, not just totals); plus an overall summary section up front. Same design/delivery/audit rules as
REQ-BANK-014. Employees cannot trigger this export.

**Acceptance**

- [ ] Endpoint gated `hasRole('BANK_MANAGEMENT')`; employee → 403.
- [ ] Per-account net change equals the difference of the two balances and the sum of
  the itemized bookings (test-pinned).

**Enforced by:** _pending (Phase 3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-016 — Dashboards

The bank landing page (`/bank`) is a **dashboard** in the design system's **D1 card
grid** layout (`proposals/bank-dashboard-varianten.html`): one `.kpi-card` per visible
account showing the current balance and the **net change over the last 30 days**
(sign-colored `.kpi-delta--pos/--neg`), plus a compact 30-day trend visualization
(server-computed inline SVG sparkline — no charting library exists or is introduced;
visual spec: `preview/components-kpi-sparkline.html`). Cards link to the account
detail; closed accounts render dimmed (`.kpi-card--closed`). Management totals render
as the `.kpi-total` aggregate strip. Bank employees see the accounts they hold grants on;
bank management (and admins) see **all** accounts plus aggregate totals (sum of
balances, total 30-day in/out). Itemized recent bookings deliberately live on the
account **detail** page, not the dashboard — the dashboard shows the net change.
Balances and deltas come from one grouped backend query (`/api/v1/bank/dashboard`) — no
per-account N+1 (REQ-DATA-003).

**Acceptance**

- [ ] Employee dashboard lists exactly the granted accounts; management/admin dashboard
  lists all accounts + totals row.
- [ ] 30-day delta equals the sum of postings in the window (test-pinned).
- [ ] Dashboard renders correctly on all four device classes (REQ-UI responsive rules).

**Enforced by:** _pending (Phases 1–2)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-017 — UI: design system, i18n, modals

The entire bank UI (and both PDFs) follows the DAS KARTELL design system
(`docs/specs/ui-design-system.md`; visual source of truth:
`.claude/skills/das-kartell-design/README.md`): Lato-only typography, brand colors,
square-first HUD styling, the four responsive device classes, and **no native browser
dialogs** (KRT modals / `showKrtConfirm` only). The design system additionally ships
**final-draft mockups for all four bank pages** (`proposals/bank-dashboard-varianten.html`,
`bank-konto-detail-varianten.html`, `bank-verwaltung-varianten.html`,
`bank-admin-varianten.html`; submodule pin ≥ `2ba5678`) plus a dedicated bank component
layer in `krt-components.css` (`.kpi-*`, `.holder-*`, `.stack-*`, `.matrix-flag`,
`.krt-modal`, `.confirm-input` — documented in the submodule `README.md` "Bank
patterns" block). The bank pages are **built to match these mockups** using the shipped
component classes; deviations need an owner decision. Every user-visible string lives
in `messages.properties` / `_de` / `_en` under new `bank.*`, `admin.bank.*` and
`nav.bank.*` keys (umlauts as `\uXXXX` escapes in `.properties`).

**Acceptance**

- [ ] No hardcoded user-visible strings in templates/JS/Java (review + lint pass).
- [ ] All confirmation flows (close account, wipe reset, reversal) use KRT modals.
- [ ] Each bank page visually matches its final-draft mockup (dashboard D1, detail K1,
  management W1 + G1/G2, admin A1 + A2) and reuses the design-system bank component
  classes instead of bespoke CSS.

**Enforced by:** _pending (Phases 2–4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-018 — API & persistence conventions

Bank endpoints live under `/api/v1/bank/**` and follow
`docs/specs/api-conventions.md` (DTO-only boundaries, MapStruct, `@Valid`, RFC 7807,
`PageResponse` with whitelisted sort fields, UTC storage, SpringDoc/`openapi.json`
upkeep) and `docs/specs/data-persistence.md` (Flyway-only schema, no N+1). Mutable rows
(`bank_account`, `bank_account_grant`, `bank_holder`) carry `@Version` and
echo it through DTOs and `data-version` DOM attributes; ledger and audit rows are
insert-only and deliberately version-less. The booking flow observes the CLAUDE.md
concurrency rules (`…WithinTransaction` pattern, no bulk updates in loops).

**Acceptance**

- [ ] ArchUnit suite passes with the bank controllers/services registered in the
  relevant rule whitelists (`BankSecurityService` as accepted gate).
- [ ] `openapi.json` regenerated in every phase PR that touches the API.

**Enforced by:** _pending (Phases 1–2)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-019 — Season independence

The bank has **no coupling** to seasons, price lines, mission finance entries, operation
payouts or job-order profit flows. No bank code reads or writes those aggregates; no
automated flow books into the bank in v1 (all bookings are manual by bank staff).
Integrations (e.g. auto-booking operation payouts) are explicitly out of scope and would
require a spec change.

**Acceptance**

- [ ] Bank services/repositories have no dependency on mission/operation/order types
  (ArchUnit-checkable package rule).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-020 — Storage, performance & integrity

PostgreSQL remains the **single datastore** for the bank (ADR-0009) — no additional
database, cache layer or event store is introduced. Balance reads are SQL aggregates
backed by a composite index on `bank_posting (account_id, created_at)`; the dashboard and
statement queries are grouped single-statement reads. A scheduled integrity job (pattern:
`task/UserSyncTask`) periodically verifies the ledger invariants (`TRANSFER` postings
sum to zero; `REVERSAL` postings are the negated mirror of the reversed transaction's
postings; no negative account balances or holder sub-balances; audit row exists for
every transaction) and reports violations as `ERROR` log events with `correlationId`.

**Acceptance**

- [ ] Dashboard and statement queries stay single-digit-statement (no N+1) under a
  seeded volume test (≥ 100 accounts / ≥ 100k postings).
- [ ] The integrity job flags a synthetically corrupted ledger in tests.

**Enforced by:** _pending (Phases 1, 5)_ · **Code:** _pending_ · **Issues:** #556

## Out of scope

- **Accounts for individual players** — an explicit owner decision: players appear only
  as holders (custody dimension, REQ-BANK-003), never as account owners.
- **Automated money flows** from missions/operations/orders into the bank (REQ-BANK-019)
  — a possible future epic, requires its own spec.
- **Interest, fees, loans, currencies other than aUEC.**
- **Bank access for org-unit members** (a member viewing "their" Staffel account) — v1
  is staff-only by design; the org units interact with the bank off-tool.
- **English PDF variants** — v1 statements/exports render German labels (from the
  message bundles, so a locale switch stays cheap later).
- **A "Bereich" (area) entity** — area accounts carry a free-form name; the org chart
  stays purely descriptive (REQ-ORG-010).

## Open questions

1. **Final role naming** — `Bank Employee` / `Bank Management` are proposals; the owner
   names the Keycloak roles before Phase 1 freezes `DataInitializer` codes.
2. **Transfer destination rule** — v1 requires the destination account to be *visible*
   to the employee (grant row). Alternative (any active account as destination) would be
   a one-line spec change; decide at Phase 1 review.
3. **Reversal permission** — v1 proposal: reversals require `BANK_MANAGEMENT` (employees
   ask management to fix mistakes). Confirm at Phase 1 review.
4. **Statement number/archival** — statements are generated on demand and not persisted;
   if the org wants numbered, archived statements, that becomes a follow-up requirement.
5. **Holders without a basetool account** — v1 requires every holder to be a registered
   tool user (picked via the user lookup, handle snapshotted). Allowing free-text
   external holders would be a small spec change; decide at Phase 1 review.

