# Specs Index — canonical requirements registry

This folder is the home for the project's **durable, canonical specifications** — the
long-lived statement of *what the software must do and why*, as distinct from the *work*
of building it (GitHub Issues / Projects / Milestones) and the *history* of how it
shipped (`CHANGELOG.md`, commits).

A spec is **docs-as-code**: it lives in the repository, is versioned in Git, and changes
only through a reviewed pull request. Every requirement then has a diff, a `git blame`,
and a single place to look — instead of being scattered across closed issues nobody
re-reads.

> **This file is a registry, not a container.** Some canonical specs predate this folder
> and still live at the repo root or inline in `CLAUDE.md`. That is fine — they are
> listed here so there is *one catalog*. Do not relocate a heavily-referenced spec just
> to move it under `docs/specs/`; register it in place.

## Registry

|                   Spec                    |                                                            Location                                                             |        Type        |              Status               |        Area        |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|--------------------|-----------------------------------|--------------------|
| Roles & permissions matrix                | [`ROLES_AND_PERMISSIONS.md`](../../ROLES_AND_PERMISSIONS.md)                                                                    | Living             | Current                           | AUTH               |
| Security & access control                 | [`security-and-access.md`](security-and-access.md)                                                                              | Living             | Current                           | AUTH/SEC           |
| Multi-org-unit tenancy & scope            | [`org-unit-tenancy.md`](org-unit-tenancy.md)                                                                                    | Living             | Current                           | ORG                |
| Org chart (Funktionsränge)                | [`org-chart.md`](org-chart.md)                                                                                                  | Living             | Current                           | ORG                |
| API conventions                           | [`api-conventions.md`](api-conventions.md)                                                                                      | Living             | Current                           | API                |
| Data & persistence                        | [`data-persistence.md`](data-persistence.md)                                                                                    | Living             | Current                           | DB/DATA            |
| Inventory Lager — append-only + grouping  | [`inventory-lager.md`](inventory-lager.md)                                                                                      | Living             | Current                           | INV                |
| Material quantities (SCU/PIECE)           | [`inv-material-quantities.md`](inv-material-quantities.md)                                                                      | Living             | Current                           | INV                |
| Whole-number amounts (currency & counts)  | [`whole-number-amounts.md`](whole-number-amounts.md)                                                                            | Living             | Current                           | MISSION/ORDERS/INV |
| Mission payout preference                 | [`mission-payout-preference.md`](mission-payout-preference.md)                                                                  | Living             | Current                           | MISSION            |
| Job-order assignee notes                  | [`orders-assignee-notes.md`](orders-assignee-notes.md)                                                                          | Living             | Current                           | ORDERS             |
| Item-order blueprint coverage             | [`orders-item-blueprint-coverage.md`](orders-item-blueprint-coverage.md)                                                        | Living             | Current                           | ORDERS             |
| Home-page "next mission" banner           | [`mission-next-banner.md`](mission-next-banner.md)                                                                              | Living             | Current                           | MISSION            |
| Mission detail page — tab layout          | [`mission-detail-tabs.md`](mission-detail-tabs.md)                                                                              | Living             | Current                           | MISSION/UI         |
| Personal inventory — blueprints (V3)      | [`personal-inventory-blueprints.md`](personal-inventory-blueprints.md)                                                          | Living             | Current                           | INV/UI             |
| Blueprint import — product name matching  | [`blueprint-import-name-matching.md`](blueprint-import-name-matching.md)                                                        | Living             | Current                           | INV                |
| Blueprint availability — list & drilldown | [`blueprint-availability-overview.md`](blueprint-availability-overview.md)                                                      | Living             | Current                           | INV/UI             |
| Observability & logging                   | [`observability.md`](observability.md)                                                                                          | Living             | Current                           | OBS                |
| UI & design system                        | [`ui-design-system.md`](ui-design-system.md) · visual source: [design skill](../../.claude/skills/das-kartell-design/README.md) | Living             | Current                           | UI                 |
| Materials category-grouping view toggle   | [`materials-overview-grouping.md`](materials-overview-grouping.md)                                                              | Living             | Current                           | UI                 |
| Squadron hangar overview — pagination     | [`hangar-squadron-overview.md`](hangar-squadron-overview.md)                                                                    | Living             | Current                           | HANGAR/UI          |
| Database migration conventions (detailed) | [`db/migration/README.md`](../../backend/src/main/resources/db/migration/README.md)                                             | Living             | Current                           | DB                 |
| Deployment                                | [`docs/deployment.md`](../deployment.md)                                                                                        | Living             | Current                           | OPS                |
| Refinery screenshot import                | [`refinery-screenshot-import.md`](refinery-screenshot-import.md)                                                                | Living             | Current — v1 shipped (Phases 0–3) | REFINERY           |
| Refinery screenshot import — master plan  | [`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md)                                                   | Historical plan    | Frozen 2026-06-10 — v1 shipped    | REFINERY           |
| SC Extractor GUI design                   | [`DESIGN_SC_EXTRACTOR.md`](../DESIGN_SC_EXTRACTOR.md)                                                                           | Design spec        | Current — implemented, binding    | REFINERY           |
| Frontend confidential OAuth2 client       | [ADR-0001](../adr/0001-frontend-confidential-oauth2-client.md) + [runbook](../OAUTH2_CONFIDENTIAL_CLIENT_MIGRATION.md)          | Decision + runbook | Accepted — implementation pending | AUTH               |

Architecture *decisions* (the "why we chose X over Y") live next door in
[`docs/adr/`](../adr/README.md), not here. A spec says what must hold; an ADR records the
decision that shaped it.

## Conventions

### 1. Living vs. historical

Every spec / plan document declares its lifecycle in a header block so a reader (human or
AI) instantly knows whether it is *current truth* or a *frozen artifact*:

```markdown
> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
```

or

```markdown
> **Doc type:** Historical plan — frozen after implementation.
> Current behaviour: see <link to the living spec or the code>. Do not edit to track new changes.
```

A **living spec** is updated in the same PR that changes the behaviour it describes. A
**historical plan** (e.g. a `*_PLAN.md` written before a feature existed) is frozen once
shipped: it keeps its value as the record of intent, but must point at the living truth so
nobody mistakes a stale plan for the current contract. This is exactly why
`SPEZIALKOMMANDO_PLAN.md` was retired once the org-unit feature went live.

### 2. Requirement IDs (traceability anchors)

Give load-bearing requirements a stable ID so they can be referenced from issues, PRs, and
tests — in both directions:

```
REQ-<AREA>-<NNN>     e.g. REQ-ORDERS-012, REQ-AUTH-003
```

`<AREA>` is drawn from the same vocabulary as the issue "Affected Area" and the labels:
`AUTH`, `ORG`, `ORDERS`, `INV`, `MISSION`, `REFINERY`, `HANGAR`, `PROMO`, `ADMIN`, `UEX`,
`UI`, `I18N`, `DB`, `OPS`. Numbers are never reused, even after a requirement is removed
(mark it superseded instead).

In a test, name the requirement it pins down:

```java
// covers REQ-ORDERS-012 — SK orders are visible to every squadron
@Test
void skOrderIsVisibleAcrossSquadrons() { ... }
```

A plain-text search for `REQ-ORDERS-012` then finds the requirement, every test that
proves it, and every issue/PR that touched it.

### 3. Acceptance criteria are the testable contract

Write acceptance criteria as checkable statements (Given/When/Then maps onto the project's
test convention). "Done" means each criterion is a green test, not an opinion. Link the
spec to the test class that enforces it.

### 4. The traceability chain

```
Spec (docs/specs, REQ-ID, acceptance criteria)
  └─ Epic issue ──┬─ Phase issue        (references the REQ-ID)
                  │     └─ PR            ("Closes #NNN", links the spec)
                  │           ├─ Commit (Conventional Commits)
                  │           └─ Test   (// covers REQ-ID)
                  │
                  └─ Milestone           (which release shipped it)
                        └─ CHANGELOG     (the user-visible realisation)
```

## Adding a new spec

1. Copy [`TEMPLATE.md`](TEMPLATE.md) to `docs/specs/<area>-<short-name>.md`.
2. Fill in the header block, the requirements (with `REQ-<AREA>-<NNN>` ids), and the
   acceptance criteria.
3. Add a row to the registry table above.
4. Open it as a PR — a spec change is reviewed like a code change.
5. When you implement it, reference the REQ-ids from the issue, the PR, and the tests.

