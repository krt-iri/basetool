> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-12.
> **Owner area:** BANK · **Related ADRs:** ADR-0009, ADR-0010, ADR-0011
> **Status:** Proposed — owner review pending (epic
> [#556](https://github.com/krt-iri/basetool/issues/556)); implementation is
> approval-gated. Each phase PR updates this spec in the same PR (acceptance boxes get
> ticked, `Enforced by` links get filled in).

# Kartell bank — accounts, ledger, grants, audit, exports

## Context & goal

DAS KARTELL runs an in-game bank for its members and units. The basetool gets a dedicated
**bank area** in which a small, dedicated staff (not regular squadron members) manages
accounts for every organizational layer — Staffeln, Spezialkommandos, areas (Bereiche),
the cartel as a whole, the cartel bank itself, dynamically named special accounts, and
per-player accounts split across the player's Star Citizen characters. Bank staff book
deposits, withdrawals and transfers; every change lands in an immutable audit log that
only administrators can read. Account statements and a management overview are exported
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
| `PLAYER`      | FK → `app_user`                                                                 | at most one per user     |

Singleton and per-owner uniqueness are enforced by partial unique indexes, not by
application convention alone.

**Acceptance**

- [ ] Creating a second `CARTEL`, `CARTEL_BANK`, per-org-unit or per-user account is
  rejected with a 409 and a stable problem code.
- [ ] Account numbers are unique, server-generated and never reused.
- [ ] New entities reference org units via an `org_unit` FK (`org_unit_id`), never
  `squadron_id` (ArchUnit rule
  `noNewJoinColumnReferencingSquadronIdOutsideGrandfatheredEntities`).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-002 — Dynamic account lifecycle, no hard delete

**All** account types — org-unit, area, cartel, cartel-bank, special and player — are
created at runtime by bank management (and admins) through the same endpoint; nothing is
seeded by migration, including the two singletons. Accounts for org units, areas and
special purposes are thereby **dynamically creatable and closable** without a migration
or restart. Closing an account requires a **zero balance** (transfer the remainder
first); a closed account becomes read-only (no postings) but stays visible to authorized
readers with its full history. Closed accounts can be reopened by bank management. The
close/reopen rules apply to every type including `PLAYER`. Accounts are **never
hard-deleted**; deactivating an org unit (soft delete, see
`SquadronService.deleteSquadron`) does not touch its bank account.

**Acceptance**

- [ ] Closing an account with a non-zero balance is rejected (409, stable code).
- [ ] Postings on a `CLOSED` account are rejected; reads and statements still work.
- [ ] Reopen restores full booking capability and is audited.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-003 — Player accounts are partitioned across Star Citizen characters

For a `PLAYER` account it must be determinable **which Star Citizen character holds which
part** of the player's balance. The bank therefore maintains, per player account, a set of
named characters (`bank_player_character`; at least one, the "main", created with the
account). **Every posting on a player account references exactly one character**; the
per-character balance is the sum of its postings. Moving value between characters of the
same player is an **intra-account rebooking** (REQ-BANK-011) and changes per-character
balances without changing the account balance. Characters with a zero balance can be
deactivated; characters are never hard-deleted while postings reference them. There is no
pre-existing character concept in the tool (`app_user` has none) — this modeling is new
and bank-local.

**Acceptance**

- [ ] A posting on a `PLAYER` account without a character reference is rejected (400).
- [ ] Postings on non-player accounts carry no character reference (DB CHECK).
- [ ] The account detail view and the statement PDF show per-character sub-balances that
  sum exactly to the account balance.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-004 — Append-only double-entry ledger

All value movements are recorded in an **append-only ledger**: a `bank_transaction`
(type, initiator, note, timestamp) with 1..n `bank_posting` rows (account, signed amount,
optional character). Transaction types: `DEPOSIT` (one positive posting), `WITHDRAWAL`
(one negative posting), `TRANSFER` (two postings summing to zero — covers
account-to-account, player-to-player and intra-account character rebookings),
`WIPE_RESET` (admin-only, REQ-BANK-013) and `REVERSAL` (corrections). A reversal's
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

No posting may take an account balance below zero — and on `PLAYER` accounts no posting
may take a **character sub-balance** below zero either (an intra-account rebooking can
only move value a character actually holds). Withdrawals and transfers are validated
against the current balance **inside the booking transaction** with an atomic/locked
balance check so concurrent bookings cannot jointly overdraw an account. Violations
surface as 409 with a stable problem code (not 500).

**Acceptance**

- [ ] Concurrent withdrawals that would jointly overdraw an account: exactly one
  succeeds (concurrency test).
- [ ] An intra-account rebooking exceeding the source character's sub-balance is
  rejected (409).
- [ ] The error response names the account and the available balance — without leaking
  data to callers who cannot see the account (403 takes precedence).

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-007 — Keycloak roles & hierarchy

Bank access is anchored on two new **Keycloak realm roles** (names proposed, owner may
rename before Phase 1 — see Open questions):

- `Bank Employee` → `ROLE_BANK_EMPLOYEE` — may use the bank area within the limits of
  their per-account grants (REQ-BANK-009).
- `Bank Management` → `ROLE_BANK_MANAGEMENT` (Bankleitung) — sees and manages
  **all** accounts, characters and grants.

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

### REQ-BANK-008 — Separation of duties: bank staff hold no org-unit membership

To act in the bank (any read or write under the bank surface, except the admin
carve-out), a user must (a) hold a basetool account and (b) have **zero
`org_unit_membership` rows** — bank staff must not be members of any Staffel,
Spezialkommando or other org unit. This is checked **at access time** on every bank gate
(`BankSecurityService`), not only at grant time: if a bank employee later joins an org
unit, their bank access (including bank-management rights) is **suspended** until the
membership is removed; their grants stay stored but inert. Admins are exempt
(REQ-BANK-010) — admin access is the maintenance carve-out and is always audited.

**Acceptance**

- [ ] Granting bank permissions to a user with a membership is rejected (409, stable
  code) with a message naming the conflict.
- [ ] A bank employee who gains a membership receives 403 on every bank endpoint and the
  frontend shows a "bank access suspended" notice instead of account data.
- [ ] Removing the membership restores access without re-granting.

**Enforced by:** _pending (Phases 1–2)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-009 — Per-account grants: view + deposit / withdraw / transfer

Fine-grained bank permissions are **app-managed grants**, not Keycloak roles
(ADR-0011): a `bank_account_grant` row per (user, account) with three independent
capability flags — `can_deposit`, `can_withdraw`, `can_transfer`. The **existence** of a
grant row gives **view access** to that account (a row with all flags false is
view-only). This expresses every required combination: deposit-only, withdraw-only,
both, and rebooking permission. Grants are managed by bank management and admins; every
grant change (create, flag change, revoke) is audited (REQ-BANK-012). Grants can only be
**created** for eligible users (REQ-BANK-008) holding the `Bank Employee` role; existing
grants become inert (not deleted) while the holder is ineligible.

**Acceptance**

- [ ] Capability matrix is enforced server-side per endpoint: deposit needs
  `can_deposit` on the account, withdrawal `can_withdraw`, transfer `can_transfer`
  on the **source** account (REQ-BANK-011 for the destination rule).
- [ ] Grant management endpoints are gated `hasRole('BANK_MANAGEMENT')` (admins pass via
  hierarchy) and reject users lacking the employee role or eligibility.
- [ ] Every grant mutation produces exactly one audit event with before/after flags.

**Enforced by:** _pending (Phase 1)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-010 — Visibility matrix

|                                 Actor                                 |                 Sees                 |                     May change                      |
|-----------------------------------------------------------------------|--------------------------------------|-----------------------------------------------------|
| Anonymous, `GUEST`, members, officers, logisticians, mission managers | **nothing** (no bank surface at all) | nothing                                             |
| Bank employee (eligible, REQ-BANK-008)                                | accounts they hold a grant on        | bookings per their capability flags                 |
| Bank management (eligible)                                            | **all** accounts, characters, grants | all bookings, account lifecycle, characters, grants |
| Admin (`ROLE_ADMIN`)                                                  | everything incl. the **audit log**   | everything incl. wipe reset (REQ-BANK-013)          |

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

A transfer (Umbuchung) moves value between two postings of one `TRANSFER` transaction.
Variants, all audited:

1. **Account → account** (e.g. Staffel → SK, area → cartel): employee needs
   `can_transfer` on the **source** account; the **destination** must be an account the
   employee can see (any grant row). Bank management and admins are unrestricted.
2. **Player ↔ player** (between different players' accounts): same rule; both postings
   name the respective character (REQ-BANK-003).
3. **Intra-account character rebooking** (same player account, two characters): requires
   `can_transfer` on that account; account balance is unchanged.

Self-transfers (same account, same character) are rejected.

**Acceptance**

- [ ] An employee with `can_transfer` on A but no grant on B cannot transfer A → B (403).
- [ ] Intra-account rebooking changes character sub-balances, not the account balance.
- [ ] All variants appear in the audit log and the statement PDF with both legs.

**Enforced by:** _pending (Phases 1, 3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-012 — Immutable, complete, admin-only audit log

Every bank mutation writes exactly one row to an **insert-only** audit table
(`bank_audit_event`, modeled after `external_sync_report` — no `@Version`, no updates):
bookings of every type, reversals, account lifecycle (create/close/reopen), character
lifecycle, every grant change, the wipe reset, and **PDF exports** (statement and
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
service books a `WIPE_RESET` transaction (one posting per account, per character for
player accounts) bringing the balance to exactly zero. History, statements and audit
trail are **preserved** — nothing is deleted. The action requires `ROLE_ADMIN`, a
KRT-styled confirmation modal (no native dialogs) with an explicit consequence text, and
writes one summarizing audit event plus the individual transactions. The operation is
idempotent (a second click on an all-zero bank is a no-op with a notice).

**Acceptance**

- [ ] After the reset every account balance and every character sub-balance is zero;
  pre-wipe statements still render correctly.
- [ ] The button sits in the admin area, is admin-only, and uses the shared danger-modal
  pattern (`btn-danger` + confirm modal).
- [ ] The audit log contains the summary event (actor, account count, total zeroed).

**Enforced by:** _pending (Phases 1, 4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-014 — Account statement PDF (Kontoauszug)

For every account they can see, bank staff can export an **account statement PDF** for a
**user-selected period** (from/to, reusing the `datetime-split-group` filter pattern):
header with account number/name/type/status, opening balance at period start, every
booking in the period (timestamp, type, counter-account where applicable, character for
player accounts, note, signed amount, running balance), closing balance, and per-character
sub-balances for player accounts. The PDF is generated backend-side with OpenPDF, follows
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
(3 months ago), in/out totals, net change and closing balance, **followed by the
itemized bookings of the window** (the owner asked for the *changes*, not just totals);
plus an overall summary section up front. Same design/delivery/audit rules as
REQ-BANK-014. Employees cannot trigger this export.

**Acceptance**

- [ ] Endpoint gated `hasRole('BANK_MANAGEMENT')`; employee → 403.
- [ ] Per-account net change equals the difference of the two balances and the sum of
  the itemized bookings (test-pinned).

**Enforced by:** _pending (Phase 3)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-016 — Dashboards

The bank landing page (`/bank`) is a **dashboard**: one card per visible account showing
the current balance and the **net change over the last 30 days** (sign-colored), plus a
compact 30-day trend visualization (server-computed inline SVG sparkline — no charting
library exists or is introduced). Bank employees see the accounts they hold grants on;
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
dialogs** (KRT modals / `showKrtConfirm` only). Every user-visible string lives in
`messages.properties` / `_de` / `_en` under new `bank.*`, `admin.bank.*` and
`nav.bank.*` keys (umlauts as `\uXXXX` escapes in `.properties`).

**Acceptance**

- [ ] No hardcoded user-visible strings in templates/JS/Java (review + lint pass).
- [ ] All confirmation flows (close account, wipe reset, reversal) use KRT modals.

**Enforced by:** _pending (Phases 2–4)_ · **Code:** _pending_ · **Issues:** #556

### REQ-BANK-018 — API & persistence conventions

Bank endpoints live under `/api/v1/bank/**` and follow
`docs/specs/api-conventions.md` (DTO-only boundaries, MapStruct, `@Valid`, RFC 7807,
`PageResponse` with whitelisted sort fields, UTC storage, SpringDoc/`openapi.json`
upkeep) and `docs/specs/data-persistence.md` (Flyway-only schema, no N+1). Mutable rows
(`bank_account`, `bank_account_grant`, `bank_player_character`) carry `@Version` and
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
postings; no negative account or character balances; audit row exists for every
transaction) and reports violations as `ERROR` log events with `correlationId`.

**Acceptance**

- [ ] Dashboard and statement queries stay single-digit-statement (no N+1) under a
  seeded volume test (≥ 100 accounts / ≥ 100k postings).
- [ ] The integrity job flags a synthetically corrupted ledger in tests.

**Enforced by:** _pending (Phases 1, 5)_ · **Code:** _pending_ · **Issues:** #556

## Out of scope

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

