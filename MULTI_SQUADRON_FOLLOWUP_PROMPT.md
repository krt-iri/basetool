# Multi-Squadron Followup — Session Prompt

> Copy the section below "→ PROMPT START" verbatim as the first user message
> in a fresh Claude Code session. The prompt is self-contained.

---

## Branch state at handover (2026-05-18)

Branch `MULTI_SQUADRON` carries the multi-tenant rollout described in
[`MULTI_SQUADRON_PLAN.md`](MULTI_SQUADRON_PLAN.md). 11 signed commits in,
roughly 70 % of the plan is done:

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

`./gradlew :backend:test` ends with **16 failures out of 1612** (build red). Buckets:

1. **11 Officer-as-admin-setup fixtures** — `JobTypeTest`, `MaterialTest`, `RefiningMethodTest`, `StarSystemTest`, `FeatureExpansionTest`, `UserJoinDateTest` have integration tests whose `@BeforeEach` (or the first MockMvc call before the actual assertion) authenticates as Officer to create the row under test, then asserts on a follow-up. After Phase 4 those Officer creates return 403, so the follow-up assertion fails. Mechanical fix: flip the create-as-Officer JWT to ADMIN in the affected test methods (or extract a `seedAsAdmin(...)` helper) and keep the assertion path. Affected test names you can grep the test report for: `testCreateJobType_Officer_Forbidden`, `testActivateJobType_Success`, `testDeleteJobType_SoftDelete_Success`, `testCreateJobType_Guest_Forbidden`, `testDeleteJobType_SoftDelete_WithParticipant_Success`, `testCreateRefiningMethod_Guest_Forbidden`, `testDeleteRefiningMethod_Officer_Forbidden`, `testDeleteStarSystem_Officer_Forbidden`, `testCreateStarSystem_Officer_Forbidden`, `shouldForbidJoinDateUpdate_WhenMemberRole`, `testCreateMaterial_Admin_Allowed` (this last one is odd — failing despite ADMIN auth, probably picks up a setup leftover from a prior Officer-create that nothing rolled back; investigate).
2. **3 `RefineryOrderServiceLifecycleTest` lifecycle stubs** — `getMissionRefineryOrders_filteredByOwner_delegatesToCombinedQuery`, `getMyRefineryOrders_withEmptyStatusList_callsOwnerOnlyVariant`, `getMyRefineryOrders_withNullStatusList_callsOwnerOnlyVariant`. Symptom is `Page.getTotalElements()` NPE because `getAllRefineryOrders` now calls `findAllScoped` / `findByStatusInScoped`, but the test class stubs `findAll(pageable)` / `findByStatusIn(...)`. Mechanical fix: update the stubs at lines ~199, 207, 220 to the `*Scoped` method names and the new `owningSquadronId` arg.
3. **1 `OperationServiceTest.shouldCreateOperation`** — `expected: not <null>`. The test stubs `operationRepository.save(any(Operation.class))).thenReturn(operation)`. With the new create path calling `squadronScopeService.currentSquadron()` before save, the result remains the same — but the test was failing in the last run despite the new `when(squadronScopeService.currentSquadron()).thenReturn(Optional.empty())` stub I added. Investigate whether strict-stubbing leaked an additional verify, or whether `@InjectMocks` is silently picking a different constructor.
4. **1 `FeatureExpansionTest.testShipWithLocation`** — `UnrecognizedPropertyException` from Jackson. Pre-existing Brittleness independent of multi-tenancy; root-cause separately.

### What is still missing from `MULTI_SQUADRON_PLAN.md`

| Block | Status | What's left |
| :--- | :--- | :--- |
| **Frontend UI** (Phase 5 incomplete) | ❌ | Sidebar squadron-switcher dropdown (admin-only); persistent context badge in the header for every user; Eigentuemer-Staffel-Spalte (admin "all squadrons" mode only) on hangar / inventory / refinery / mission list templates; dual squadron badges (Auftraggeber + Erstellt durch) on Job Order list + detail pages; visual marker on the inventory-item-linked-to-job-order rows when the item belongs to a foreign squadron; `app.title` made generic + suffixed with the active squadron context. |
| **Frontend DTO mirror records** (Phase 3 follow-up) | ❌ | Backend `MissionListDto`, `JobOrderDto`, `InventoryItemDto`, `RefineryOrderDto`, `OperationDto`, `ShipDto` need to expose the owning / creating / requesting squadron as a mini-record (id + shorthand). MapStruct mappers must populate it. Then the frontend mirror records in `frontend/src/main/java/.../model/dto/` get the same fields in the same commit — the [[feedback_backend_frontend_dto_mirror]] rule. Without this, the UI can't render the squadron columns/badges. |
| **JobOrderHandover cross-squadron guard** (Phase 3 follow-up) | ❌ | `JobOrderHandoverService.createHandover` must validate before each `InventoryItem` write that `item.jobOrderId.equals(currentOrder.id)`; throw `IllegalStateException` (translates to 400 via `GlobalExceptionHandler`) when it doesn't. Then add `JobOrderHandoverService` to the staffel-scoped whitelist in `ArchitectureTest.staffelScopedServicesMustWireSquadronOrAuthHelper` (it would need an `AuthHelperService` dep for the audit stamp). The handover record itself should record the executing user and their squadron — the audit trail the plan asks for. |
| **Test-fixture cleanup (16 failures)** | ⚠️ | See bucket list above. After the fixes, `./gradlew :backend:test` should be green. |
| **Cross-squadron positive test matrix** (Phase 6) | ❌ | Unit tests for `SquadronScopeService` (currentSquadronId per role, canSeeMission with internal vs. non-internal, canEditMission strict, canSeeInventoryItem strict, cross-squadron-by-user-A-vs-B disjoint result sets per aggregate). MissionRepository integration-style tests for the is_internal cross-staffel clause. JobOrder cross-staffel read + edit positive tests (User A creates, Logistician B edits → 200). JobOrderHandover cross-squadron InventoryItem write + the negative case where the item is not linked to the order. Lager-View vs Job-Order-Kontext disjoint test. Stick to Mockito unit tests for the pure logic; for repository tests, the existing test profile uses H2 + `flyway.enabled=false` so be careful — see [`db/migration/README.md`](backend/src/main/resources/db/migration/README.md) "Tests" section. |
| **`ROLES_AND_PERMISSIONS.md` full matrix rewrite** | ⚠️ | The notice + a few rows are updated; the full Officer column needs a systematic re-read against the @PreAuthorize annotations now in the controllers, then the matrix re-published. The current dot pattern (✅/❌) per role per endpoint should stay. |
| **`README.md` multi-tenant section** | ❌ | New §3.x "Multi-squadron rollout" between §3 (deployment) and §4 (Development & Testing). Brief: design summary, IRIDIUM canonical UUID, switcher endpoint, what Officers lost in Phase 4, where to read the design. Pointer to `MULTI_SQUADRON_PLAN.md` for the architecture details. |
| **Phase 7 migrations `V84`–`V86`** | ❌ | Intentionally deferred. Only land them after at least one production deploy of V80–V83 (two-phase drop rule in `db/migration/README.md`). `V84` tightens NOT NULL on `app_user.squadron_id`, `owning_squadron_id` on the 5 aggregates, and `creating_squadron_id` + `requesting_squadron_id` on `job_order`. `V85` removes the legacy `squadron` field from the JPA `JobOrder` entity and relaxes the NOT NULL on the column. `V86` drops the column. Do NOT do this yet — flag it as a separate PR after the next prod release. |

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

### Suggested order of attack

1. **Test-fixture cleanup** first (mechanical, removes the red signal). Aim for `./gradlew :backend:test` green.
2. **JobOrderHandover guard** + add `JobOrderHandoverService` to the ArchUnit whitelist + an `AuthHelperService` dep. Add a unit test for the guard.
3. **DTO + MapStruct + Frontend-mirror** changes for the squadron fields. This unblocks the UI work.
4. **Frontend UI** — sidebar switcher dropdown, context badge, squadron columns on lists, dual badges on Job Order. Verify on the four device classes (smartphone / tablet / desktop / ultrawide).
5. **Cross-squadron positive test matrix** for `SquadronScopeService`, `MissionService`, `InventoryItemService`, `JobOrderHandoverService`.
6. **`README.md` multi-tenant section** + **`ROLES_AND_PERMISSIONS.md` full matrix rewrite**.
7. **PR open with the full branch.** Do NOT include the `V84`–`V86` migrations yet.

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

- [ ] `./gradlew :backend:test :frontend:test :backend:checkstyleMain :backend:spotbugsMain :frontend:checkstyleMain :frontend:spotbugsMain` all green.
- [ ] Backend `./gradlew :backend:bootRun` against the dev stack starts cleanly (no Hibernate validate errors).
- [ ] Admin user can switch squadron via the sidebar dropdown; the badge reflects the active selection; the staffel-scoped lists filter accordingly; switching back to "all squadrons" restores the cross-staffel view.
- [ ] Member user sees only their squadron's data on Hangar / Inventory / Refinery / Operation lists; sees non-internal missions of other squadrons; cannot edit foreign-squadron data through the direct paths.
- [ ] Job Order list/detail shows both squadron badges; filter "Nur eigene Staffel" matches on either field. Handover succeeds across squadrons when the inventory item is linked to the order; rejects when it isn't (with the guard from item 2).
- [ ] `ROLES_AND_PERMISSIONS.md` matrix matches what the controllers actually enforce.
- [ ] `README.md` has the multi-tenant section.
- [ ] All commits on `MULTI_SQUADRON` are signed (`git log --pretty=format:'%h %G?' main..HEAD` shows `G` everywhere).
- [ ] `V84`–`V86` are still NOT in the branch (intentional — they ship in the next release iteration).

---

## → PROMPT START

Continue the multi-squadron rollout on branch `MULTI_SQUADRON` (worktree at
`D:\NC\Software\Coding\Java\KRT\basetool\.claude\worktrees\beautiful-keller-9397fa`).
The design lives in `MULTI_SQUADRON_PLAN.md`, the running audit trail in
`CHANGELOG.md`, and the multi-tenant invariants in `CLAUDE.md` → section
"Multi-squadron tenancy (CRITICAL)". 70 % of the plan is already merged
into the branch across 12 signed commits; what remains is documented in
`MULTI_SQUADRON_FOLLOWUP_PROMPT.md` at the worktree root. **Read that file
first**, then attack the open items in the suggested order (test-fixture
cleanup → JobOrderHandover guard → DTO/mapper/mirror → frontend UI →
cross-squadron tests → README/ROLES_AND_PERMISSIONS refresh). Treat the
acceptance-criteria list at the end of that file as the definition of done
for this follow-up.

Hard constraints (do not rediscover):
- Always use `./gradlew`, never the IDE runner; commits stay signed
  (`commit.gpgsign=true`, key `5D8D0D3958ED70A2`).
- Backend DTO field → Frontend mirror record in the SAME commit.
- `.properties` Umlauts as `\uXXXX`, Markdown Umlauts as UTF-8.
- Spotless + Checkstyle + SpotBugs must stay green at every commit.
- Migrations V80–V83 are deployed-shape and immutable; the V84–V86 tightening
  + legacy-VARCHAR-drop chain is INTENTIONALLY deferred to a follow-up
  release per the two-phase rule in `backend/src/main/resources/db/migration/README.md`.

Start by running `git log --oneline main..MULTI_SQUADRON` and `git status`
to confirm the branch state, then read `MULTI_SQUADRON_FOLLOWUP_PROMPT.md`
end-to-end before changing any file.

## ← PROMPT END
