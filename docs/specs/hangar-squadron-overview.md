> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-12.
> **Owner area:** HANGAR/UI · **Related ADRs:** none

# Squadron hangar overview — pagination & server-side filter

## Context & goal

The squadron hangar page (`/hangar/squadron`) aggregates the scoped fleet into one row per
ship type (count + fitted count, with an ADMIN/OFFICER-only per-ship drill-down). The page
originally fetched up to 1000 rows in one request and filtered them client-side, which
stops scaling once a fleet grows past a screenful and silently truncates beyond the fetch
cap. This spec pins the listing contract after the rework to true server-side pagination:
the table pages across **all** entries that exist in the caller's scope, and the filter is
applied by the backend so it spans the whole fleet rather than the currently rendered rows.

Who sees which rows (the OrgUnit scope triple, the strict-staffel rule, the role-shaped
owner drill-down) is **not** redefined here — see
[`org-unit-tenancy.md`](org-unit-tenancy.md) and `HangarService`.

## Requirements

### REQ-HANGAR-001 — Squadron overview paginates and filters server-side

The squadron overview must be a true server-side paginated listing: page metadata counts
ship **types** (the grouped rows), the user chooses between 10, 50 and 100 entries per
page, and the optional ship-type filter is evaluated by the backend across every type in
scope. No fetch cap may silently truncate the fleet.

**Acceptance**

- [ ] `GET /api/v1/hangar/squadron-overview` honours `page`/`size` and returns page
  metadata (`totalElements`/`totalPages`) that counts distinct ship types — never the
  underlying ships (a GROUP-BY count-query pitfall).
- [ ] The endpoint's optional `search` parameter filters case-insensitively on ship-type
  *or* manufacturer name; types without a manufacturer still match on their own name
  (LEFT-JOIN semantics). Blank input means "no filter".
- [ ] The frontend page offers exactly the page sizes 10 / 50 / 100 (shared
  `pageSizePicker` fragment, same trio and default 50 as the blueprint availability
  overview's REQ-INV-013); any other client-supplied `size` snaps back to the default
  before reaching the backend. The picker hides while the total fits the smallest size,
  where switching could never change anything.
- [ ] Changing the page size re-enters at page 0, and pagination/page-size links preserve
  an active search term — switching the size never silently drops the filter.
- [ ] The filter input submits to the server (GET form); the page renders distinct empty
  states for "no ships in scope" vs. "no match for this search", and the filter stays
  clearable when a search yields nothing.
- [ ] The scope rules and the role-shaped owner drill-down of
  [`org-unit-tenancy.md`](org-unit-tenancy.md) are unaffected: filtered and paginated
  results pass through the same `ScopePredicate` as before.

**Enforced by:** `HangarIntegrationTest`, `HangarControllerTest`, `HangarServiceTest`,
`HangarPageControllerMvcTest` · **Code:** `HangarController`, `HangarService`,
`ShipRepository#countShipsByType`, `HangarPageController`,
`frontend/src/main/resources/templates/hangar-squadron.html` · **Issues:** —

## Out of scope

- The personal hangar (`/hangar`) and the admin per-user hangar — they keep their own
  listing behaviour.
- The drill-down rendering mechanics (details-row class toggle) — a UI implementation
  detail, kept consistent with the blueprint overview's REQ-INV-012 fix.
- The shared pagination fragment's look — governed by the design system
  ([`ui-design-system.md`](ui-design-system.md)).

## Open questions

None.
