# ADR-0028 — Bereich/OL bank access (AREA/CARTEL) via the OrgUnitBankAccessService seam

- **Status:** Accepted — implementation pending (epic #692)
- **Date:** 2026-06-19
- **Deciders:** @greluc, Claude
- **Related:** spec REQ-BANK-027 · REQ-BANK-008 · REQ-BANK-021 · REQ-BANK-022 · ADR-0011 · ADR-0020 · ADR-0026 · issue #692 · #699

## Context

The bank is **org-unit-blind** (REQ-BANK-008, ADR-0011): `BankSecurityService` and the ledger decide
access only from the two bank roles and `bank_account_grant`, pinned by ArchUnit
(`bankClassesMustNotConsultOrgUnitScope`). ADR-0020 added the *only* sanctioned bridge,
`OrgUnitBankAccessService` (a deliberately non-`Bank*` name; a second ArchUnit pin
`orgUnitAwareBankSeamIsContainedToOneClass` keeps it the sole bridge), through which officers/leads see
their org unit's balance and file confirm-before-post booking requests (REQ-BANK-021/022). The
restructure (epic #692, Q4) needs **Bereich members to reach their Bereich's `AREA` account and the OL
the `CARTEL`/Kartell account** with that same non-bank-staff function, plus **view-only drill-down** into
subordinate accounts. `BankAccountType` already has `ORG_UNIT`, `AREA` (free-form `areaName`, no FK),
`CARTEL` (singleton). The ORG_UNIT drill-down already cascades for free once the oversight scope expands
(ADR-0026).

## Decision

We will **link `AREA` accounts to a Bereich and `CARTEL` to the OL**, and extend **only**
`OrgUnitBankAccessService`:

- **View cascades down:** `listOverseenBalances()` returns the caller's own-level account **and** all
  subordinate accounts in their oversight scope (Bereichsleitung → its `AREA` account + all child
  Staffel/SK `ORG_UNIT` accounts; OL → `CARTEL` + all `AREA` + all `ORG_UNIT` accounts).
- **Requests are own-level only (Q4 asymmetry):** `createBookingRequest()` is permitted only on the
  caller's own-level account (officer → squadron `ORG_UNIT`; Bereich → `AREA`; OL → `CARTEL`).
  Subordinate accounts reached by drill-down are **view-only** — no request.
- `BankSecurityService`, `BankLedgerService`, the ledger and the grant model stay **untouched and
  org-unit-blind**; both ArchUnit pins stay green. The confirm-before-post flow, overdraft/holder checks
  and `ACCOUNT_GRANT` notifications are unchanged.
- The officer flow from epic #666 is preserved exactly (no regression).

## Consequences

- Bereich/OL get balance view + own-level requests + subordinate-account drill-down with **zero**
  bank-domain change; all new logic lives in the one auditable seam.
- The view⊇request asymmetry is explicit and deliberate (money is sensitive; only the owning level may
  initiate a request on an account).
- Cost: `AREA` gains a Bereich link (migration + backfill) and `CARTEL` an OL link; the seam learns to
  match `AREA`/`CARTEL` accounts by the caller's Bereich/OL membership in addition to the existing
  `ORG_UNIT`-by-oversight match.

## Alternatives considered

- **Allow requests on subordinate accounts too** — rejected by Q4: drill-down into a subordinate account
  is view-only; only the owning level initiates requests on it.
- **Keep `AREA` free-form `areaName`** — rejected: duplicate/typo AREA accounts per Bereich are a silent
  data-corruption risk; a Bereich FK fixes cardinality (a unique index + autocomplete is the fallback if
  the FK is deferred).
- **Put the admin-drill-down logic in a `Bank*` class** — rejected: it would violate
  `bankClassesMustNotConsultOrgUnitScope`; any new bridge must be non-`Bank*` and added to the
  containment pin.

