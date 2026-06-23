# Architecture Decision Records (ADRs)

An ADR captures **one architecturally significant decision** — the context that forced a
choice, the decision itself, and the consequences we accept by making it. ADRs answer
*"why is it built this way?"* long after the people who decided have moved on.

Specs (in [`docs/specs/`](../specs/INDEX.md)) say *what must hold*. ADRs say *why we chose
this approach over the alternatives*. The two reference each other.

## When to write one

Write an ADR when a decision is **hard to reverse** or **non-obvious**: a framework or
protocol choice, a security posture, a data-model trade-off, a cross-cutting pattern (e.g.
the `…WithinTransaction` concurrency rule). Routine, easily-reversed choices do not need
one.

## Format

We use a lightweight [MADR](https://adr.github.io/madr/)-style format — copy
[`0000-template.md`](0000-template.md).

- **Numbering:** zero-padded, sequential, never reused — `0001`, `0002`, …
- **Filename:** `NNNN-kebab-case-title.md`.
- **Immutable once Accepted.** Do not rewrite an accepted ADR to reflect a *new* decision.
  Write a new ADR that supersedes it, and set the old one's status to
  `Superseded by ADR-NNNN`. The record of *why it changed* is the whole point.

## Status lifecycle

|          Status          |                                  Meaning                                  |
|--------------------------|---------------------------------------------------------------------------|
| `Proposed`               | Under discussion; not yet binding.                                        |
| `Accepted`               | The decision is in force (implementation may still be pending — note it). |
| `Superseded by ADR-NNNN` | Replaced by a later decision.                                             |
| `Deprecated`             | No longer applies; not directly replaced.                                 |

## Index

|                                    ADR                                    |                                                     Title                                                      |                      Status                       |
|---------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| [0001](0001-frontend-confidential-oauth2-client.md)                       | Frontend as a confidential OAuth2 client (PKCE + secret)                                                       | Accepted — implementation pending                 |
| [0002](0002-whole-number-amounts.md)                                      | Whole-number amounts: value-based validation, reject-not-round, display-only rounding                          | Accepted                                          |
| [0003](0003-inventory-append-only-group-on-read.md)                       | Inventory: append-only entries with group-on-read display                                                      | Accepted                                          |
| [0004](0004-ownerless-leadership-missions.md)                             | Ownerless leadership ("Bereichsleitung") missions                                                              | Accepted                                          |
| [0005](0005-ownerless-leadership-operations.md)                           | Ownerless leadership ("Bereichsleitung") operations                                                            | Accepted                                          |
| [0006](0006-operation-participant-visibility.md)                          | Operation visibility for mission participants                                                                  | Accepted                                          |
| [0007](0007-client-side-vlm-screenshot-extraction.md)                     | Client-side VLM screenshot extraction with manual JSON upload (epic #439)                                      | Accepted                                          |
| [0008](0008-refinery-extract-json-contract.md)                            | `RefineryExtract` JSON as the frozen cross-repo contract                                                       | Accepted                                          |
| [0009](0009-bank-postgresql-single-datastore.md)                          | PostgreSQL stays the single datastore for the Kartell bank                                                     | Proposed                                          |
| [0010](0010-bank-double-entry-append-only-ledger.md)                      | Bank ledger: append-only double-entry with compute-on-read balances                                            | Accepted — holder coupling superseded by ADR-0039 |
| [0011](0011-bank-authorization-model.md)                                  | Bank authorization: coarse Keycloak roles + app-managed per-account grants                                     | Proposed                                          |
| [0012](0012-frontend-krtfetch-json-mutations-csrf-retry.md)               | Standardize frontend mutations on krtFetch + JSON with session/meta CSRF + retry-on-403                        | Accepted                                          |
| [0013](0013-frontend-bfcache-history-restore-reload.md)                   | Refresh on bfcache history-restore with a global `pageshow` reload                                             | Accepted                                          |
| [0014](0014-notification-system-architecture.md)                          | Notification system: per-user inbox produced by after-commit domain events                                     | Accepted                                          |
| [0015](0015-notification-data-driven-rule-engine.md)                      | Notification recipients via a data-driven rule engine                                                          | Accepted                                          |
| [0016](0016-notification-transport-polling-sse.md)                        | Notification delivery: in-app polling baseline, SSE push as enhancement                                        | Accepted                                          |
| [0017](0017-default-blueprints-admin-curated-materialized.md)             | Default blueprints: admin-curated set materialised per-user, non-removable                                     | Accepted                                          |
| [0018](0018-desktop-ingest-gateway-device-grant.md)                       | Desktop one-click ingest: dedicated gateway + Keycloak device grant                                            | Accepted — implementation pending                 |
| [0019](0019-frontend-reauth-on-client-authorization-required.md)          | Frontend recovery from `client_authorization_required` + single-flight token refresh                           | Accepted                                          |
| [0020](0020-bank-org-unit-aware-access-seam.md)                           | Org-unit officer/lead bank access via a single non-`Bank*` seam                                                | Accepted                                          |
| [0021](0021-bank-off-ledger-booking-requests.md)                          | Confirm-before-post booking requests as a mutable, off-ledger aggregate                                        | Accepted                                          |
| [0022](0022-bank-booking-request-notifications-account-grant-selector.md) | Bank booking-request notifications via an `ACCOUNT_GRANT` selector kind                                        | Accepted                                          |
| [0023](0023-manufacturer-uex-company-alias-merge.md)                      | Merge UEX duplicate companies onto one manufacturer via a company-id alias table                               | Accepted                                          |
| [0024](0024-opt-in-global-blueprint-sharing.md)                           | Opt-in global blueprint sharing overrides org-unit scoping (read-only)                                         | Accepted                                          |
| [0025](0025-org-hierarchy-data-model.md)                                  | Org hierarchy: Bereich + Organisationsleitung as `org_unit` kinds with a parent FK                             | Accepted — implementation pending                 |
| [0026](0026-cascading-scope-without-admin.md)                             | Cascading org-unit scope without admin rights, computed in one descent helper                                  | Accepted — implementation pending                 |
| [0027](0027-bereich-ol-aggregate-ownership.md)                            | Bereich and Organisationsleitung as direct owners of org-unit-scoped aggregates                                | Accepted — implementation pending                 |
| [0028](0028-bank-bereich-ol-access-seam.md)                               | Bereich/OL bank access (AREA/CARTEL) via the `OrgUnitBankAccessService` seam                                   | Accepted — implementation pending                 |
| [0029](0029-org-chart-visibility-decoupled-from-profit-eligibility.md)    | Org chart visibility decoupled from `is_profit_eligible` (org-wide chart)                                      | Accepted                                          |
| [0030](0030-discord-federation-first-login-membership-gate.md)            | Discord federation via an owned Keycloak SPI with a fail-closed first-login membership gate                    | Accepted — Track 1 implemented                    |
| [0031](0031-live-mission-sync-over-presence-websocket.md)                 | Live multi-user mission sync over the presence WebSocket (section-key relay)                                   | Accepted                                          |
| [0032](0032-frontend-single-resilience-pass-at-webclient-filter.md)       | Single frontend resilience pass at the WebClient filter (drop redundant AOP layer)                             | Accepted                                          |
| [0033](0033-scmdb-net-export-and-structural-tag-matching.md)              | scmdb.net export as a fourth import shape + structural tag matching                                            | Accepted                                          |
| [0034](0034-anonymous-outsider-mission-visibility.md)                     | Anonymous outsider view of public missions is operational by design, minus payout + comment                    | Accepted                                          |
| [0035](0035-blueprint-craftability-from-own-stock.md)                     | Blueprint craftability computed server-side from the user's own "My Inventory" stock                           | Accepted                                          |
| [0036](0036-discord-link-recognised-from-federated-identity.md)           | Discord link recognised from the Keycloak federated identity, not the import-time attribute                    | Accepted                                          |
| [0037](0037-shared-multi-domain-activity-audit-log.md)                    | One shared `audit_event` table (domain discriminator) for the four new area audit logs; bank keeps its own     | Accepted                                          |
| [0038](0038-admin-retention-purge-of-audit-logs.md)                       | Admin-controlled retention purge: delete each audit log's entries older than a cutoff (amends ADR-0037)        | Accepted                                          |
| [0039](0039-bank-holder-ledger-decoupled-from-accounts.md)                | Bank: holder custody decoupled from accounts via a second append-only ledger (global, may go negative)         | Accepted                                          |
| [0040](0040-bank-staff-are-holders-and-employee-administration-access.md) | Bank staff are holders (auto-registration from bank roles) + employee bank-administration access               | Accepted                                          |
| [0041](0041-bank-in-game-transfer-fee.md)                                 | Bank: factor the in-game transfer fee into holder-initiated transfers (carve out, credit net, amends ADR-0039) | Accepted                                          |

