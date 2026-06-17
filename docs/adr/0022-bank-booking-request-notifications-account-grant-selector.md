# ADR-0022 — Bank booking-request notifications via an `ACCOUNT_GRANT` selector kind

- **Status:** Accepted
- **Date:** 2026-06-17
- **Deciders:** @greluc, Claude
- **Related:** spec REQ-BANK-026 · REQ-NOTIF-011 · REQ-NOTIF-007 · ADR-0015 · issue #666

## Context

Epic #666 (owner-requested addition) needs the bank staff who can act on a new booking request
to be notified: the **bank management** and the **employees granted on the target account**.
The notification system already provides a data-driven recipient rule engine (ADR-0015,
REQ-NOTIF-007) with `SPECIFIC_USER`, `ROLE` and `ORG_RELATIVE_ROLE` selectors resolved by
`RecipientResolutionService`. "Bank management" maps cleanly onto a `ROLE` selector, but
"employees granted on *this* account" cannot be expressed: the account is **per-event** (it
differs for every request) and no existing selector reads it, mirroring how a `JobOrderCreated`
event's org unit is per-event.

## Decision

We will add one new selector kind, **`ACCOUNT_GRANT`**, that resolves to every user holding a
`bank_account_grant` on the **account carried by the event**. The event contract gains a
`default UUID contextAccountId()` (returning `null` for non-bank producers), exactly paralleling
the existing `contextOrgUnits()` used by `ORG_RELATIVE_ROLE`. `RecipientResolutionService` grows
a `resolveAccountGrantHolders(accountId)` backed by `BankAccountGrantRepository.findByAccountId`.
A seeded default rule (V160) maps `BANK_BOOKING_REQUEST_CREATED` to a notification with a
`ROLE` selector (`BANK_MANAGEMENT`) and an `ACCOUNT_GRANT` selector, excluding the requester.
No schema migration is needed for the selector table — the kind reads the account from the event,
not from a selector column, and the type/event-type columns carry no CHECK (open enums).

## Consequences

- The new use case ships as **rule data + one selector kind**, with the existing
  after-commit/async pipeline, SSE push and admin rule editor reused unchanged.
- The rule stays admin-editable at runtime, like UC1.
- Cost: the generic `RecipientResolutionService` now depends on `BankAccountGrantRepository` — a
  deliberate, narrow coupling consistent with its existing dependencies on `UserRepository` /
  `OrgUnitMembershipRepository`. (The notification engine is not a `Bank*` class and does not
  touch `OwnerScopeService`, so no bank ArchUnit rule is affected.) "Appropriately authorized for
  the account" is interpreted as *holds a grant on it*; the per-action capability flag still gates
  who may actually confirm (REQ-BANK-023).

## Alternatives considered

- **Resolve recipients inside the bank service and create notifications directly** — rejected:
  bypasses the data-driven engine (REQ-NOTIF-007), hardcoding recipients and losing the admin
  editability and the shared push/inbox pipeline.
- **A static account id stored on the selector** — rejected: the account is per-event, so a
  static selector value cannot work; reading it from the event matches the `ORG_RELATIVE_ROLE`
  precedent.
- **Filter grant holders by the request's capability (deposit→`can_deposit`)** — deferred:
  bakes bank-capability semantics into the generic engine; notifying all grant holders is simpler
  and the confirm gate already enforces capability.

