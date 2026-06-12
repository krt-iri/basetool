> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-12.
> **Owner area:** INV/UI · **Related ADRs:** none

# Blueprint availability overview — owner drill-down hot path

## Context & goal

The leadership oversight page "Blueprint-Verfügbarkeit" (#364) lists, per product, how
many members of the caller's oversight org units own the crafting blueprint, with a lazy
per-row drill-down that fetches the owning members' display names. Expanding a row is the
page's hot path: it is clicked repeatedly while browsing, so it must stay responsive
regardless of how many blueprints the table lists and how many users exist in the system.
This spec pins the performance contract of that drill-down after a regression where every
expand briefly froze the UI (a `tr:has(details[open])` sibling CSS rule re-evaluated style
across the whole table per toggle) and the admin-scoped owners fetch ran a redundant
all-owners table scan whose result was echoed back as an unbounded SQL `IN` list.

The functional contract of the page (who may see it, what the list aggregates, that owner
identity is exposed as display name only) is documented on
`PersonalBlueprintOverviewService` / `PersonalBlueprintOverviewController` and in
[`ROLES_AND_PERMISSIONS.md`](../../ROLES_AND_PERMISSIONS.md).

## Requirements

### REQ-INV-012 — Owner drill-down stays responsive

Expanding or collapsing a blueprint row must not perform work that scales with the size of
the table, and the owners fetch must not perform work that scales with the number of users
outside the requested product.

**Acceptance**

- [ ] Toggling a row's details shows/hides only that row's companion details-row, via a
  class toggled on the element itself by `blueprint-overview.js`; no CSS rule may
  derive the companion row's visibility from a `:has()`/sibling selector that the
  browser has to re-evaluate across the table on every toggle.
- [ ] For the admin "all org units" scope, the owners lookup queries by product key alone
  (`findAllByProductKey`); it must not enumerate all distinct owner subs first nor
  pass them back as an `IN` restriction.
- [ ] Non-admin scopes keep the owner-restricted lookup (product key + in-scope member
  subs), resolved server-side — the client cannot widen the scope, and the
  multi-user data-isolation rule is unaffected.

**Enforced by:** `PersonalBlueprintOverviewServiceTest` · **Code:**
`PersonalBlueprintOverviewService`, `frontend/src/main/resources/static/js/blueprint-overview.js`
· **Issues:** #364

## Out of scope

- The availability list aggregation itself (page load, one query per view) and its
  scope-gate — see `PersonalBlueprintOverviewService` and `OwnerScopeService`.
- The identically-structured expandable rows on other pages (e.g. the squadron hangar);
  they have their own row counts and are governed by their own specs.

## Open questions

None.
