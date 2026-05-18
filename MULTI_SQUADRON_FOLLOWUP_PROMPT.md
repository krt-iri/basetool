# Multi-Squadron Followup — Session Prompt

> Copy the section below "→ PROMPT START" verbatim as the first user message
> in a fresh Claude Code session. The prompt is self-contained.

---

## Branch state at handover (2026-05-18, second session)

Branch `MULTI_SQUADRON` carries the multi-tenant rollout described in
[`MULTI_SQUADRON_PLAN.md`](MULTI_SQUADRON_PLAN.md). After the second
session there are 17 signed commits. The acceptance criteria for the
first follow-up are met **except** for the frontend UI work
(switcher dropdown / context badge / squadron columns / dual badges)
and the intentionally-deferred V84–V86 migration chain. Recent commits
added by this session:

```
6842f71 G  test(multi-tenant): SquadronScopeService unit-test matrix
fc7c918 G  docs(multi-tenant): README §3.4 + ROLES_AND_PERMISSIONS post-Phase-4 verification
0bc7b4c G  feat(multi-tenant): surface squadron mini-record on aggregate DTOs
b089d0c G  docs(multi-tenant): clarify JobOrderHandoverService whitelist deferral
4b190cc G  test(multi-tenant): align fixtures with Phase 3+4 contract
```

Below are the original 12 commits from the first session (unchanged):

```
56fef86 G  chore(multi-tenant): apply spotless + sync openapi.json after Phase 3 closure
cf2e5e2 G  docs(multi-tenant): document the read-side filter wave + extend CLAUDE.md
effc752 G  feat(multi-tenant): detail-view PreAuthorize + MDC squadronId + ArchUnit guard
eb89ce9 G  feat(multi-tenant): squadron filter on list endpoints + aggregate checks (Phase 3 closure)
efa9268 G  test(multi-tenant): adapt test suite to Phase 3 + Phase 4 contract
28764cb G  docs(multi-tenant): CHANGELOG entry + ROLES_AND_PERMISSIONS update (Phase 6 partial)
2449df0 G  feat(multi-tenant): frontend proxy for active-squadron switcher (Phase 5 partial)
da0466c G  feat(multi-tenant): lock admin area to admins only (Phase 4)
3769be1 G  feat(multi-tenant): stamp squadron on aggregate creates + hangar filter (Phase 3)
eff9a75 G  feat(multi-tenant): squadron-aware auth helpers and switcher endpoint (Phase 2)
496adac G  feat(multi-tenant): introduce squadron scope on aggregate roots (Phase 1)
```

### What is already in place

- **Data model:** Flyway V80–V83 (IRIDIUM seed at canonical UUID `00000000-0000-0000-0000-000000000001`; `app_user.squadron_id`; `owning_squadron_id` on `mission`/`operation`/`ship`/`inventory_item`/`refinery_order`; `job_order.creating_squadron_id` + `requesting_squadron_id`). All new columns nullable in this release; `V84`–`V86` for NOT NULL tightening + legacy `job_order.squadron` VARCHAR drop are deferred to a follow-up release per the two-phase rule.
- **Authorization layer:** [`SquadronScopeService`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/SquadronScopeService.java) with `currentSquadronId()` / `currentSquadron()` / `canSee*` / `canEdit*` for every staffel-scoped aggregate. [`AuthHelperService`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/AuthHelperService.java) gains `currentUserId()` + `isAdmin()`. [`MeController`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/MeController.java) exposes `/api/v1/me/active-squadron` (GET/PUT/DELETE, PUT/DELETE admin-only). [`MeFrontendController`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/controller/MeFrontendController.java) proxies the POST from browser.
- **Service / repository filtering:** Mission (`searchMissions` with the `owning = :ctx OR is_internal = false` clause), Inventory direct (`findGlobalByFilters` + `getAggregatedInventory` with `owningSquadronId`), Refinery (`findAllScoped` / `findByStatusInScoped`), Operation (`findAllScoped`), Hangar (`findAllScoped` + `countShipsByType`). All create paths stamp the squadron from the entity owner (Ship / InventoryItem / RefineryOrder) or from `SquadronScopeService.currentSquadron()` (Operation, sub-Mission). Job Order stamps `creatingSquadron` (immutable) + `requestingSquadron` (editable) and keeps the legacy `squadron VARCHAR` mirrored.
- **`@PreAuthorize` on detail views:** `MissionController.GET /{id}` → `canSeeMission(#id)`. `InventoryItemController` PUT `/{id}`, PATCH `/{id}/delivered`, PUT `/{id}/note` → `canEditInventoryItem(#id)`. `RefineryOrderController` GET/PUT/DELETE `/{id}` → `canSee/canEditRefineryOrder(#id)`. `OperationController` GET `/{id}` → `canSeeOperation(#id)`; PUT combines `hasRole('MISSION_MANAGER')` with `canEditOperation(#id)`. Job-Order endpoints stay rollenbasiert (cross-staffel workspace).
- **Admin lockdown (Phase 4):** every `@PreAuthorize("hasAnyRole('ADMIN','OFFICER')")` on the admin area is tightened to `hasRole('ADMIN')`. Officer keeps squadron-internal capabilities (Mission management, Hangar write incl. `resetAllFittedStatus`, Refinery, Logistician via role hierarchy, JobOrder).
- **MDC `squadronId` field** in [`CorrelationIdFilter`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/logging/CorrelationIdFilter.java); sentinels `all` / `none` / `anonymous`, defensive `try`/`catch`.
- **ArchUnit guard** `staffelScopedServicesMustWireSquadronOrAuthHelper` in [`ArchitectureTest`](backend/src/test/java/de/greluc/krt/iri/basetool/backend/ArchitectureTest.java) fails the build if `MissionService` / `InventoryItemService` / `RefineryOrderService` / `HangarService` / `OperationService` / `JobOrderService` stop injecting one of the auth services. `JobOrderHandoverService` is intentionally NOT yet in the whitelist (waits for the audit-trail stamp landing).
- **Frontend proxy:** `MeFrontendController` translates `POST /me/active-squadron` from the browser form into the backend PUT / DELETE. i18n keys `squadron.switcher.{label,all,activated,cleared}` + `squadron.context.badge` already in all three `messages*.properties` (German uses `\uXXXX` escapes per the project rule).
- **Docs:** `CHANGELOG.md` and `CLAUDE.md` carry full coverage of the multi-tenant rollout; `MULTI_SQUADRON_PLAN.md` is the design source of truth.
- **Migrations verified:** standalone Postgres 18 + Flyway 11 CLI run applied V1–V83 cleanly (0.313 s, including the IRIDIUM-rewrite edge case at non-canonical UUID). Backend `./gradlew :backend:bootRun` started cleanly against the test stack — Hibernate `ddl-auto=validate` passed end-to-end.

### Current test situation

`./gradlew :backend:test :frontend:test` is **green** (1613/1613 backend,
all frontend tests). The 15 fixture / stub failures listed in the first
session's handover have all been corrected (`4b190cc`). Spotless,
Checkstyle and SpotBugs are clean on both modules.

### What is still missing from `MULTI_SQUADRON_PLAN.md`

| Block | Status | What's left |
| :--- | :--- | :--- |
| **Frontend UI** (Phase 5 incomplete) | ❌ | **Largest remaining work item.** Sidebar squadron-switcher dropdown (admin-only); persistent context badge in the header for every user; Eigentuemer-Staffel-Spalte (admin "all squadrons" mode only) on hangar / inventory / refinery / mission list templates; dual squadron badges (Auftraggeber + Erstellt durch) on Job Order list + detail pages; visual marker on the inventory-item-linked-to-job-order rows when the item belongs to a foreign squadron; `app.title` made generic + suffixed with the active squadron context. The backend DTOs already expose the squadron data (see `0bc7b4c`) so Thymeleaf templates can dereference `${item.owningSquadron?.shorthand}` directly. Switch endpoint already lives at `POST /me/active-squadron` (frontend proxy `MeFrontendController`); the i18n keys `squadron.switcher.{label,all,activated,cleared}` + `squadron.context.badge` are seeded in all three `messages*.properties` files. |
| **Frontend DTO mirror records** (Phase 3 follow-up) | ✅ | Done in `0bc7b4c`. `SquadronReferenceDto` (id + name + shorthand) exists on both sides; `MissionListDto`, `JobOrderDto`, `InventoryItemDto`, `RefineryOrderDto`, `OperationDto`, `ShipDto` expose the owning/creating/requesting squadron via this mini-record; MapStruct wires it through `SquadronMapper.toReferenceDto` plus the `uses` chain on the six affected mappers. `openapi.json` regenerated. |
| **JobOrderHandover cross-squadron guard** (Phase 3 follow-up) | ⚠️ | The InventoryItem-belongs-to-order guard already lives in `JobOrderHandoverService.createHandover` at lines 116-119 and is exercised by `JobOrderHandoverServiceTest.createHandover_shouldThrowException_whenItemDoesNotBelongToOrder` + `…whenJobOrderIsNullOnInventoryItem`. The remaining piece — stamping the executing user + squadron on the handover row as a real audit trail — is **deferred to the V84-V86 follow-up release** because it needs a column-add migration and a DTO/mapper/frontend mirror pass. `JobOrderHandoverService` is intentionally NOT in the `ArchitectureTest` staffel-scoped whitelist yet for the same reason; the comment at lines 542-554 documents the prerequisite. See `b089d0c`. |
| **Test-fixture cleanup (15 failures)** | ✅ | Done in `4b190cc`. `./gradlew :backend:test :frontend:test` is fully green again. |
| **Cross-squadron positive test matrix** (Phase 6) | ⚠️ | **`SquadronScopeServiceTest` exists** (`6842f71`, 437 lines, 27 tests) covering currentSquadronId per role, canSee/canEdit for every aggregate, the Mission `is_internal=false` cross-staffel escape, and the admin/member distinction. **Still open:** MissionRepository integration-style tests for the SQL `is_internal` clause, JobOrder cross-staffel read+edit positive tests, JobOrderHandover cross-squadron InventoryItem positive test (negative case already covered), Lager-View vs Job-Order-Kontext disjointness test. Use Testcontainers + Postgres for repository-level tests; the test profile uses real Postgres (see `application-test.yml`), so a `@DataJpaTest`-style approach works. |
| **`ROLES_AND_PERMISSIONS.md` full matrix rewrite** | ✅ | Done in `fc7c918`. Notice updated to reflect the post-Phase-4 audit completion; the inert `USER_MANAGE` authority on OFFICER is called out; a new sixth bullet describes the multi-squadron visibility model. The endpoint matrix was verified against every `@PreAuthorize` annotation in the backend controllers and lines up with the implementation. |
| **`README.md` multi-tenant section** | ✅ | Done in `fc7c918`. New §3.4 "Multi-squadron rollout" between Deployment and Development & Testing. |
| **Phase 7 migrations `V84`–`V86`** | ❌ | Intentionally deferred. Only land them after at least one production deploy of V80–V83 (two-phase drop rule in `db/migration/README.md`). `V84` tightens NOT NULL on `app_user.squadron_id`, `owning_squadron_id` on the 5 aggregates, and `creating_squadron_id` + `requesting_squadron_id` on `job_order`. `V85` removes the legacy `squadron` field from the JPA `JobOrder` entity and relaxes the NOT NULL on the column. `V86` drops the column. Do NOT do this yet — flag it as a separate PR after the next prod release. The `JobOrderHandover` audit-trail columns (executing_user_id, executing_squadron_id) should ride with this same migration train so the schema change lands once. |

### Constraints the new session must obey (do not rediscover the hard way)

- **Always use the Gradle wrapper.** Never the IDE test runner. `./gradlew :backend:test` etc.
- **`@PreAuthorize` lives at the controller/service boundary**, never inside business logic. ArchUnit enforces this.
- **DTOs at boundaries.** Never expose JPA entities; never return `Page<JpaEntity>` either. ArchUnit enforces this too.
- **Backend DTO field → Frontend mirror record in the same commit.** Build pipeline doesn't catch the asymmetry; 500 lands at render time in prod ([[feedback_backend_frontend_dto_mirror]]).
- **`.env.test` over the production `.env`.** Never copy the prod `.env`, `keystore.p12`, or `realm-export.json` into the worktree. Recipe in `README.md` §4.4. Test stack tear-down uses `down --volumes`. ([[feedback_env_test_isolation]]).
- **Mockito unit tests preferred** for new tests. TestContainers is available now that Docker works locally, but the existing convention leans Mockito-first.
- **German Umlauts:** in `.properties` files MUST be `\uXXXX`; in Markdown files MUST be literal UTF-8. Don't mix.
- **Git in English.** Commits, PR titles/bodies, issue text. The MULTI_SQUADRON_PLAN.md is in German (it's a design doc, not a Git artifact), so that's fine.
- **GPG-signed commits.** `commit.gpgsign=true` is already set; the user signs with key `5D8D0D3958ED70A2`. If pinentry times out non-interactively, use `git -c commit.gpgsign=false commit ...` and then re-sign via `GIT_SEQUENCE_EDITOR=true git rebase main --exec "git commit --amend --no-edit -S"` before reporting.
- **`commit.gpgsign=false` is OK to bypass once and re-sign later, but the final state must have every multi-tenant commit signed `G`.**
- **`./gradlew check` must be green before the next handover.** That means `:backend:test` must pass (fix the 16 failures listed above) and Checkstyle + SpotBugs stay clean.
- **Spotless before each push.** `./gradlew :backend:spotlessApply :frontend:spotlessApply`.
- **Don't drop or rewrite migration files V80–V83.** They've been verified end-to-end against Postgres 18.

### Suggested order of attack (for the next session)

1. **Frontend UI (Phase 5 finish)** — the only large remaining slice.
   Touch order:
   1. `fragments/sidebar.html` — admin-only squadron-switcher dropdown
      wired to `POST /me/active-squadron`. The `MeFrontendController`
      already accepts `squadronId=<uuid>` (or empty to clear). Pull the
      squadron list from a new backend call (or a new
      `SquadronFrontendController.list()` that proxies the existing
      `GET /api/v1/squadrons`).
   2. `fragments/head.html` or `fragments/header.html` — persistent
      context badge for every user. Backend already exposes the active
      squadron via `GET /api/v1/me/active-squadron`; the frontend can
      load it once per request via `MeFrontendController` and pass it
      to the layout fragment.
   3. `mission-list.html` / `hangar*.html` / `inventory*.html` /
      `refinery-orders*.html` — add the "Eigentuemer-Staffel" column,
      visible only when the admin is in "all squadrons" mode. The DTO
      already carries `owningSquadron.shorthand` (`0bc7b4c`).
   4. `job-orders.html` and `job-order-detail.html` — dual squadron
      badges (Auftraggeber = `requestingSquadron`, Erstellt durch =
      `creatingSquadron`). Add a "Nur eigene Staffel"-style filter that
      matches on either field.
   5. Inventory rows that link to a job order from a foreign squadron
      get a subtle marker (e.g. orange border-left or a small chip);
      the data is available via `inventoryItem.owningSquadron` vs.
      `jobOrder.creatingSquadron`.
   6. `app.title` and the message keys that build it — make generic
      and suffix with the active squadron's shorthand when one is
      selected.
   7. Verify on **smartphone / tablet / desktop / ultra-wide**
      breakpoints per the CLAUDE.md responsive-design rule.
2. **Cross-squadron repository / integration tests** —
   `MissionRepository` `is_internal` cross-staffel positive case,
   JobOrder cross-staffel read + edit positive cases. Use the existing
   Postgres test profile, not H2; `@SpringBootTest` + `@Transactional`
   gives the cleanest fixture isolation.
3. **PR open with the full branch.** Do NOT include the `V84`–`V86`
   migrations yet.

The follow-on V84–V86 migration train should bundle the NOT-NULL
tightening, the legacy `job_order.squadron` two-phase drop, and the
JobOrderHandover executing-user / executing-squadron audit columns
into the same release iteration so the schema change happens once
end-to-end.

### Files you'll touch most often

- Backend services: `backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/{Squadron,AuthHelper,Mission,InventoryItem,RefineryOrder,Operation,JobOrder,JobOrderHandover}Service.java`
- Backend controllers: same package's `controller/` subtree
- Backend DTOs: `backend/src/main/java/de/greluc/krt/iri/basetool/backend/model/dto/`
- Backend mappers: `backend/src/main/java/de/greluc/krt/iri/basetool/backend/mapper/`
- Frontend mirror DTOs: `frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/model/dto/`
- Frontend templates: `frontend/src/main/resources/templates/{fragments/sidebar.html,mission-list.html,job-orders*.html,hangar*.html,inventory*.html,refinery-orders*.html}`
- i18n: `frontend/src/main/resources/messages{,_de,_en}.properties`
- Tests: `backend/src/test/java/de/greluc/krt/iri/basetool/backend/` (existing structure mirrors prod)
- Plan + audit trail: [`MULTI_SQUADRON_PLAN.md`](MULTI_SQUADRON_PLAN.md), [`CHANGELOG.md`](CHANGELOG.md), [`CLAUDE.md`](CLAUDE.md), [`ROLES_AND_PERMISSIONS.md`](ROLES_AND_PERMISSIONS.md)

### Acceptance criteria for the next handover

- [x] `./gradlew :backend:test :frontend:test :backend:checkstyleMain :backend:spotbugsMain :frontend:checkstyleMain :frontend:spotbugsMain` all green.
- [ ] Backend `./gradlew :backend:bootRun` against the dev stack starts cleanly (no Hibernate validate errors). *(Not re-verified in the second session — schema-only changes since the first session's successful run.)*
- [ ] Admin user can switch squadron via the sidebar dropdown; the badge reflects the active selection; the staffel-scoped lists filter accordingly; switching back to "all squadrons" restores the cross-staffel view. *(Open — frontend UI is the remaining slice.)*
- [ ] Member user sees only their squadron's data on Hangar / Inventory / Refinery / Operation lists; sees non-internal missions of other squadrons; cannot edit foreign-squadron data through the direct paths. *(Backend enforces this — covered by `SquadronScopeServiceTest`. The visible UI bits are blocked on the frontend slice.)*
- [ ] Job Order list/detail shows both squadron badges; filter "Nur eigene Staffel" matches on either field. Handover succeeds across squadrons when the inventory item is linked to the order; rejects when it isn't (with the guard from item 2). *(Backend guard verified; UI badges open.)*
- [x] `ROLES_AND_PERMISSIONS.md` matrix matches what the controllers actually enforce.
- [x] `README.md` has the multi-tenant section.
- [x] All commits on `MULTI_SQUADRON` are signed (`git log --pretty=format:'%h %G?' main..HEAD` shows `G` everywhere).
- [x] `V84`–`V86` are still NOT in the branch (intentional — they ship in the next release iteration).

---

## → PROMPT START

Continue the multi-squadron rollout on branch `MULTI_SQUADRON` (worktree at
`D:\NC\Software\Coding\Java\KRT\basetool\.claude\worktrees\beautiful-keller-9397fa`).
The design lives in `MULTI_SQUADRON_PLAN.md`, the running audit trail in
`CHANGELOG.md`, and the multi-tenant invariants in `CLAUDE.md` → section
"Multi-squadron tenancy (CRITICAL)". After two follow-up sessions the
backend is feature-complete with 17 signed commits: data model, auth,
read-side filters, detail-view `@PreAuthorize`, MDC squadronId,
ArchUnit guard, DTO surface (`SquadronReferenceDto` on six aggregate
DTOs with frontend mirror), test suite green (1613/1613 backend; all
frontend), `SquadronScopeServiceTest` covering the access matrix,
`README §3.4` documenting the rollout, `ROLES_AND_PERMISSIONS.md`
matrix verified against every `@PreAuthorize`. The **only major slice
left is the frontend UI** — sidebar squadron-switcher dropdown,
persistent context badge, owning-squadron columns on the list pages,
dual badges on Job Order, foreign-squadron markers on inventory rows,
and the generic `app.title` with active-squadron suffix. The remaining
follow-up details (cross-squadron repository / integration tests, the
`V84-V86` tightening + legacy-VARCHAR-drop chain + the
`JobOrderHandover` audit-trail columns bundled with it) are scoped in
`MULTI_SQUADRON_FOLLOWUP_PROMPT.md` at the worktree root. **Read that
file first**, then attack the frontend UI in the order it documents.

Hard constraints (do not rediscover):
- Always use `./gradlew`, never the IDE runner; commits stay signed
  (`commit.gpgsign=true`, key `5D8D0D3958ED70A2`).
- Backend DTO field → Frontend mirror record in the SAME commit.
- `.properties` Umlauts as `\uXXXX`, Markdown Umlauts as UTF-8.
- Spotless + Checkstyle + SpotBugs must stay green at every commit.
- Migrations V80–V83 are deployed-shape and immutable; the V84–V86
  tightening + legacy-VARCHAR-drop chain (plus the `JobOrderHandover`
  audit columns scoped to ride along) is INTENTIONALLY deferred to a
  follow-up release per the two-phase rule in
  `backend/src/main/resources/db/migration/README.md`.
- Frontend templates can dereference the new squadron DTO fields
  directly (e.g. `${item.owningSquadron?.shorthand}`,
  `${jobOrder.creatingSquadron?.shorthand}`) — the backend already
  populates them via MapStruct (`0bc7b4c`).

Start by running `git log --oneline main..MULTI_SQUADRON` and `git status`
to confirm the branch state, then read `MULTI_SQUADRON_FOLLOWUP_PROMPT.md`
end-to-end before changing any file.

## ← PROMPT END
