> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-15.
> **Owner area:** FE/UI · **Related ADRs:** ADR-0012, ADR-0013

# Frontend AJAX mutations — krtFetch, krtCsrf & fragment swaps

## Context & goal

Working with data in the `frontend` module must not reload the page. Every create / update / delete
/ toggle / reorder / filter / paginate interaction updates the DOM **in place** through one shared
client foundation, so users keep scroll and focus and the app ships a small payload per edit instead
of re-rendering the whole document. This spec is the durable contract behind epic #571; the
architecture decision (and the rejected alternatives — htmx, cookie-CSRF, app-wide Alpine) is
ADR-0012. Phase 0 (#572) builds the foundation + exemplars; per-area conversions are the epic's
child issues (#573–#582).

The foundation lives in [`krt-fetch.js`](../../frontend/src/main/resources/static/js/krt-fetch.js),
loaded globally from `fragments/head.html`. It exposes `window.krtFetch` (`write`, `swap`,
`syncVersion`) and `window.krtCsrf` (`headers`, `token`, `refresh`). The toast/confirm infrastructure
(`showFrontendSuccessToast`, `showFrontendErrorToast`, `showKrtConfirm`) is the design-system-mandated
replacement for native dialogs (see [`ui-design-system.md`](ui-design-system.md)).

## Requirements

### REQ-FE-001 — No full-page reload on a successful mutation

A create / update / delete / toggle / reorder interaction must update only the changed DOM nodes and
must not navigate the document (no `window.location.reload()`, no redirect-follow) on success. The
**only** sanctioned reloads are (1) the optimistic-lock conflict path, where the user explicitly
accepts a reload via `showKrtConfirm`, and (2) the bfcache history-restore refresh of REQ-FE-008 —
which is not a success-path reload but a freshness guarantee for a document the browser replays from
its back/forward cache.

**Acceptance**

- [ ] After the action, the URL and document are unchanged and the affected node(s) reflect the new
  state (badges, totals and status cells re-derived in the success handler, not by a reload).
- [ ] No `location.reload()` runs on the success path; the only success-path-adjacent reloads are a
  user-accepted optimistic-lock confirm and the bfcache history-restore refresh (REQ-FE-008).
- [ ] Derived UI that lives **outside** the swapped fragment is refreshed too — an emptied list
  restores its "no entries" placeholder, and a count or server-derived value shown in a separate
  modal/header reflects the new state (when a fragment swap cannot cover it, the handler patches it
  explicitly, e.g. the hangar home-location ship count re-rendered on modal open, the category/alias
  placeholder rebuilt on the last delete, the order-detail header re-pulled on **every** status
  change so a reactivated order's reassigned priority renders in place — not only on the terminal
  transition).
- [ ] A write that **replaces the entity identity** (delete-old + create-new — e.g. a full-amount
  inventory transfer that appends a new target item and deletes the source) re-keys the affected DOM
  row + its controls to the new id/version, so a follow-up action targets the live entity, not the
  removed one. An optimistic native control (checkbox / select) reverts to its prior state on a
  failed write, so the rendered state never diverges from the persisted value.
- [ ] The submit control is disabled for the duration of the in-flight write and re-enabled when it
  settles, so a double-click cannot fire a duplicate create or a stale-version delete. Enforced
  centrally: `krtFetch.write` auto-captures the triggering form's submit button and toggles it
  (raw-`fetch` write paths — order/refinery create, the mission-data helper — guard it explicitly).

**Enforced by:** per-area Playwright e2e (no-navigation assertion) +
`MaterialsCategoryEmptyStateInPlaceE2eTest` (empty-state restore) +
`MaterialCollectionTransferInPlaceE2eTest` (row re-keyed after a transfer) +
`JobOrderReactivatePriorityInPlaceE2eTest` (header refreshed on reactivate) · **Code:**
`krt-fetch.js`, the converted page handlers · **Issues:** #571, #572

### REQ-FE-002 — All writes go through `krtFetch` + `krtCsrf`

Mutations use `krtFetch.write(...)`; no page may hand-roll a `fetch` write or a CSRF-header read.
`krtCsrf` is the single source of truth, reading the freshest `meta[name="_csrf"]` /
`meta[name="_csrf_header"]` tags. Hard-coded header names (e.g. the former `members.html`
`X-CSRF-TOKEN`) are forbidden.

**Acceptance**

- [ ] A new write call site uses `krtFetch.write` / `krtCsrf.headers()` — no bespoke CSRF block.
- [ ] No template or JS hard-codes the CSRF header name.

**Enforced by:** code review + grep guard in review · **Code:** `krt-fetch.js` · **Issues:** #572

### REQ-FE-003 — `syncVersion` propagates the optimistic-lock version

On a successful write whose response carries a `version`, `krtFetch` writes that value to the
target container **and every descendant `[data-version]`**, so the user's next action on the same
aggregate sends the fresh version and does not 409. When propagation cannot be made complete in
place, the handler reloads deliberately (the conflict-confirm path) rather than leaving a stale
version. This is the client half of the `@Version` rules in `CLAUDE.md`.

**Backend obligation.** The contract above only holds if the mutation response carries the
_post-write_ version. A service (or class-`@Transactional` controller) that maps the response DTO
**inside the still-open transaction** must persist with `saveAndFlush`, not `save`: the `@Version`
increment is otherwise deferred to commit — _after_ the DTO is mapped — so the response carries the
**stale pre-flush** version, the client writes it back in place, and the next action 409s. This does
**not** apply to entities whose section version is a manual in-memory counter bumped before mapping
(e.g. `Mission.coreVersion` / `scheduleVersion` / `flagsVersion` / `partyLeadVersion`), which is
already current in the DTO regardless of flush timing, nor to writes whose handler re-renders the
fragment from a fresh server `GET` (the re-swap re-reads the committed version). See the
optimistic-locking rules in `CLAUDE.md`. (A 2026-06 area audit added
`JobOrderService.updateJobOrder` to the `saveAndFlush` set — its edit-modal version writeback
otherwise 409s the next consecutive order edit.)

**Acceptance**

- [ ] A second consecutive action on the same row/aggregate after a successful write does not
  produce a 409.
- [ ] All related `[data-version]` attributes in the DOM context hold the new version after success.
- [ ] A write whose response feeds an in-place version writeback returns the flushed
  (post-increment) version — `saveAndFlush` wherever the DTO is mapped inside the transaction.

**Enforced by:** per-area e2e "double-action" assertion · **Code:** `krt-fetch.js` (`syncVersion`)
· **Issues:** #571

### REQ-FE-004 — CSRF stays session/meta-based with transparent retry-on-403

The CSRF token repository and handler are unchanged (`HttpSessionCsrfTokenRepository` +
`XorCsrfTokenRequestAttributeHandler`). An authenticated `GET /csrf` returns `{headerName, token}`.
On a bare `403` from a write, `krtFetch` refetches the token once, updates the `_csrf` meta tags,
and retries the request exactly once before surfacing the error. `/csrf` rejects anonymous callers
(it sits under the `authenticated()` catch-all). This is additive, not a binding security-model
change (ADR-0012).

**Acceptance**

- [ ] `GET /csrf` returns the current header name + token for an authenticated session and does not
  serve an anonymous caller a token.
- [ ] A write with a stale token succeeds after exactly one transparent token-refresh + retry; a
  genuinely failing write still surfaces its error (no infinite retry).

**Enforced by:** `CsrfTokenControllerMvcTest` + forced-stale-token e2e · **Code:**
`CsrfTokenController`, `krt-fetch.js` (`krtCsrf.refresh`, retry path) · **Issues:** #572

### REQ-FE-005 — Lists / filters / pagination are in-place fragment swaps

Filtering, sorting and paginating a list use `krtFetch.swap`, which loads the controller's
`?fragment=results` HTML fragment into the results container and intercepts in-container pagination
/ sort anchors (`a.page-btn[href]` and opted-in `a[data-swap][href]`) so paging stays in place. This
fixes the regression where pagination anchors inside an AJAX results container triggered a full
navigation while filtering did not.

**Acceptance**

- [ ] Changing a filter swaps the results without navigating.
- [ ] Clicking a pagination/sort control inside the results container swaps in place (no full page
  load) and preserves the active filter query.
- [ ] A swap whose GET is **redirected** (e.g. an expired session bounced to the login page) or
  returns a **non-OK** status does not inject the body: `krtFetch.swap` bails, leaves the stale
  container untouched, surfaces an optional caller-supplied error toast, and never paints a whole
  page into the small results container. On a backend read failure the mission-detail fragment
  branch returns a **section-sized inline error fragment (HTTP 200)**, not a `redirect:/missions`.

The same fragment-swap mechanism also re-renders **non-list page sections** after a sub-mutation
when an in-place DOM patch would be too fragile (structural add/delete, server-derived render state,
or a value duplicated across panes). The mission-detail page (#574) does this: a crew/finance/owner-
manager write re-renders just its section via `GET /missions/{id}?fragment={crew-board,finance,mgmt}`
into a stable `#…-results` container, and every per-element handler inside (drag-drop, action
buttons, role selects) is delegated on the persistent container so it survives the swap. The
order-detail page (#575) does the same: claim create/edit/withdraw, the inventory unlink and the edit
modal re-render the `header` / `materials` / `aggregated` sections via `GET /orders/{id}?fragment=…`
(a server-derived aggregate like the claims "Offen" amount would desync a partial patch), and the
order-list drag-drop priority reorder re-renders the whole queue the same way (the backend reshuffles
every sibling's priority). The fresh `data-version` carried by the re-rendered fragment satisfies
REQ-FE-003 for free, and on a backend read failure the fragment branch returns a section-sized error
fragment, never a redirect the swap would follow into the container.

The operations area (#576) combines the patterns through a set of `X-Requested-With` write twins
(`createOperationAjax` / `updateOperationAjax` / `deleteOperationAjax`) beside the classic
POST→redirect fallbacks. Creating or deleting from the list re-renders `#operations-results` via the
existing `GET /operations?fragment=results` swap (the page exposes `window.krtOperationsReload` so the
write handlers reuse the active filter query); editing on the detail page patches the version input
and the title in place from the twin's `{version, name, status}` (the backend PUT echoes the
persisted operation in-transaction, so no second round-trip can observe a concurrent writer's
`version+2` or mask an already-committed write — no navigation); deleting
from the detail page navigates back to the list (the entity is gone — REQ-FE-006). The payout
paid-out toggle was already in-place — its bespoke CSRF read now goes through `krtCsrf` (REQ-FE-002).

The inventory area (#577) is converted in two parts; **part A** does the create + metadata writes.
Book-in (`addInventoryItemAjax`, a multipart `X-Requested-With` twin beside the classic
POST→redirect) navigates to the source listing on success and keeps the form with a toast on a 422
(REQ-FE-006); the note and association edits (`inventory-my` / `inventory-admin`) drop their
hand-rolled CSRF reads and per-`[data-id]`/`[data-note-for]` version loops for `krtCsrf`
(retry-on-403) + `krtFetch.syncVersion` against the acting control's `.tree-row--leaf` container (so
the next edit on that row does not 409 — REQ-FE-003), and the note edits plus the bulk-selection
guards surface their success/error outcome through the shared `showFrontendSuccessToast` /
`showFrontendErrorToast` globals — the page-local `showInventoryToast(type, msg)` helper delegates to
them rather than to a non-existent page element; the material-collection owner/location transfer
and delivered toggle move onto `krtFetch.write` (per-row patch; a full-amount transfer deletes the
source item server-side and appends a new target item still linked to the job order, returning that
target DTO — the row is re-keyed in place to the new id/version rather than removed, and a failed
delivered toggle reverts the checkbox); and the admin delete-all clears the grouped table via the
existing
filter swap instead of a reload. **Part B** does the quantity-changing list writes. The single
**book-out** modal (`inventory-my` / `inventory-admin`, all three of DISCARD / TRANSFER / SELL)
submits in place through `krtFetch.write`, reusing the existing `POST /inventory/{id}/transfer`
proxy (the same backend book-out endpoint — equivalent for every type, since
`InventoryItemBookOutDto` only requires `amount` + `version`); on success it re-renders the grouped
table through the filter swap (`filterMyInventory` / `filterInventory`) because a book-out regroups
server-side, and `scu-decimal-input.js`'s capture-phase submit listener canonicalises + validates the
amount before the page handler runs (which respects `event.defaultPrevented`). **Bulk-checkout**
(personal inventory only) now posts to a new `POST /inventory/bulk-checkout` frontend proxy — the
page previously called the backend `/api/v1/inventory/bulk-checkout` path directly, which had no
matching frontend route, so the bulk action never reached the backend; the proxy relays the call and
`propagateBackendError`s a failure, and the client re-swaps the grouped table + resets the bulk bar
instead of reloading. Both reuse the shared `frontend.ajax.conflict.*` strings for the
OPTIMISTIC_LOCK reload-confirm.

The refinery **screenshot-extract import** (#591) applies the same fragment-swap idea to a
**multipart POST** rather than a GET: an `X-Requested-With` import twin
(`RefineryOrderPageController.importExtractAjax`) returns the pre-filled create-form fragment
(`refinery-orders-create :: refineryImportFormBody`), and a bespoke `fetch` swaps it into the stable
`#refineryImportFormContainer` then dispatches `krt:swapped` (`krtFetch.swap` is GET-only and cannot
carry the upload). Every branch — success and each error (invalid/oversized file, unparseable JSON,
backend reject) — renders inline in the swapped region, never a redirect; the classic
`POST→redirect` proxy stays the no-JS fallback. This required making `datetime-splitter.js`
**swap-safe**: an idempotent per-group `init` guarded by `data-krt-dt-initialized` plus a
`krt:swapped` auto-reinit, so the create form's date widget — and any future fragment-swapped
datetime group app-wide — re-initialises exactly once after a swap (no double-bound listeners, no
duplicate error div).

The asset-management area (#578) — **hangar**, **ship-data** and the **personal-inventory** /
**blueprints** pages — combines the twin + fragment-swap patterns through a set of `X-Requested-With`
write twins beside the classic `POST→redirect` fallbacks. **Hangar** create/edit (the modal form),
delete and the bulk home-location set submit through `krtFetch.write` to header-gated twins
(`addShipAjax` / `updateShipAjax` / `deleteShipAjax` / `setHomeLocationAjax`) and re-render the ship
table via the existing `GET /hangar?fragment=results` swap (the server multi-key sort makes a
client-side row insert too fragile); the import + delete-all flows drop their post-action
`location.reload()` for the same swap and their two hand-rolled CSRF reads move onto `krtCsrf` (the
multipart import keeps a bespoke `fetch` minus the JSON `Content-Type`). Because the action + per-row
edit buttons live inside the swapped `#hangar-results` fragment they are bound through `krtEvents`
`data-trigger` delegation (and the live ship-type filter is a delegated document listener) so they
survive every re-swap. **Ship-data** flips each visibility toggle in place (button label + secondary
style + dimmed opacity, the hidden input updated so the next toggle sends the opposite value) and the
admin reset-all-fitted toasts + closes its modal, all without navigating. **Personal-inventory**
add/edit/delete go through JSON twins that re-render the existing `#pi-results` list fragment.
**Blueprints** note-edit returns the fresh blueprint from its twin so the master row (note + version
+ note-marker badge) and the detail pane are patched in place (the selection and the loaded recipe
survive); remove, batch-add and import-apply re-render the new `#krt-bp-list` fragment (`recipe.js`
re-inits its master/detail wiring and `personal-inventory-blueprints.js` resyncs the header counts on
`krt:swapped`), and the variant CSRF helpers in `personal-inventory-blueprints.js` /
`-import.js` were replaced by `krtCsrf` / `krtFetch`. Every twin relays a backend failure as
`problem+json` (the shared `propagateBackendError` helper) so an `OPTIMISTIC_LOCK` drives the
sanctioned reload-confirm; a missing required field on a create twin is a `422` `VALIDATION`
`problem+json` rather than the 500 the frontend `@ControllerAdvice` would make of a `@Valid`
`@RequestBody` bind failure.

The bank area (#579) converts the last AJAX-then-`location.reload()` writes — money operations and
the account / holder / grant lifecycle — to in-place fragment swaps without touching the already
complete `BankProxyController`. The generic `bank.js` form dispatcher keeps its bespoke inline
field-error rendering (`.bank-field-error` slots + the `CODE_FIELD` overdraft / self-transfer /
holder-inactive mapping — bank 409s render at the field, never as a reload-confirm toast) but moves
its CSRF onto the shared `krtCsrf` with retry-on-403, and replaces the success reload with a server
re-render of the region named by the form's `data-refresh` attribute. The account-detail money writes
(deposit / withdraw / transfer / rebook / reverse) swap the whole `accountBody`
(`GET /bank/accounts/{id}?fragment=accountBody`) because the balance, the holder distribution and the
booking modals' distribution-derived holder selects are all backend aggregates a JS patch would
desync — and the money forms carry no `@Version` (the ledger is append-only), so an immediate second
booking cannot 409. The manage lifecycle writes swap `manageBody` (tab-nav + active panel together, so
the `.tab-count` aggregates and every trigger button's fresh `data-field-version` re-render
atomically, fixing the shared deactivate/reactivate-modal stale-version trap), and grant create /
revoke swap `grantsMatrix` honouring the active `view` / `accountId` / `userId` filter (#573). The one
genuinely isolated single-row write — a grant capability flag toggle — stays a precise dom-patch
(`button.on` + the row's `data-can-*` + `krtFetch.syncVersion` from the `BankGrantDto` response). If a
write succeeds but only its follow-up refresh GET bounces, the swap surfaces a dedicated "saved, but
reload" message rather than the generic "action failed" text, so a committed money booking is never
mistaken for a failure.

The **promotion** admin + management pages (#580) drop their last `AJAX-then-location.reload()`
writes for in-place fragment swaps. The two admin pages re-render their list region after every
mutation: **topics/categories** create / edit / delete and the up/down **reorder** swap
`promotion-admin-topics :: topicsResults` into `#pa-topics-results`, and the **rank-requirements**
create / edit / delete + group-delete swap `promotion-admin-rank-requirements :: ranksResults` into
`#ar-results`. A full server re-render is exactly what re-syncs every card's `@Version`, sort order
and first/last arrow state, so a second reorder can no longer 409 — and the reorder no longer relies
on a non-existent GET-by-id proxy route (it now reads the full DTO each PUT needs straight from the
card's edit-button data attributes). The **manage** matrix already saved grades in place through its
serialised save queue; #580 finishes it by recomputing each touched member's **eligibility chips**
once the queue drains, via a lightweight per-member `promotion-manage :: eligibilityCell` swap, and
by replacing the optimistic-lock **409 reload** with an in-place `promotion-manage :: matrixBody`
re-render that rebuilds every row with a fresh `@Version` (the client-side collapse / sort / filter
state is restored on `krt:swapped`). All three pages' bespoke `getCsrfToken` / `getCsrfHeader` +
`apiCall` helpers were retired onto `krtCsrf` (shared reader + retry-once-on-403); the client-side
rank filter and the manage CSV export are untouched.

The organisation / members / profile area (#581) converts the org-chart inline editor, the member
list and the profile + home-page writes. The org-chart position operations (add / reassign / rename /
vacate / remove) drop the `setTimeout(location.reload)` that followed each `send()` for a `chartBody`
fragment swap (`GET /org-chart?fragment=chartBody`): the chart is a flat, CSS-connected pre-order ARIA
tree whose add affordances and vacant/filled transitions are derived aggregate state, so a per-node
patch would desync the "+" buttons and the roving-tabindex order — the swap re-stamps every
`data-version` and the inline JS re-inits the tree keyboard navigation on `krt:swapped`; the bespoke
`csrfHeaders()` reader moves onto `krtCsrf` with retry-on-403. The chart's horizontal scroll is
captured before the focus-returning `closeModal()` and re-applied across animation frames until the
freshly-swapped tree's layout settles, so the offset survives the in-place refresh on every engine.
The member list converts the delete (a `@DeleteMapping` JSON twin whose success re-swaps the results
fragment so pagination and the SK column stay coherent) and reverts — rather than reloads — a failed
logistician / mission-manager toggle in place; its filter `fragment` param changes from `boolean` to
`String` so it binds the `krtFetch.swap` helper's `fragment=results` value (a `boolean` param silently
400'd that swap). The member-edit page saves through an in-place `{field: message}` twin (REQ-FE-007).
The profile payout-preference form joins the description form on `krtFetch.write` (both echo the one
shared user-row version), and the home-page mark-announcement-read posts in place and removes its
control. The one reload deliberately kept is the sidebar active-OrgUnit switcher: switching the
org-unit re-scopes every list, count and entity on the page through `OwnerScopeService`, so the
existing controlled full navigation (`POST /me/active-org-unit` → `_referer` redirect) is the correct
UX — an "in-place" swap would amount to re-rendering the whole page anyway (REQ-ORG-\*).

The **admin CRUD** long tail (#582 — the last epic child) converts the remaining admin reference-data
pages. The list-level CRUD on **mission-data** (squadrons / job-types / frequency-types create / edit
/ delete / activate, plus the frequency-type drag-drop **reorder**) and **special-commands** (SK
create / edit / delete / activate; member add / remove / role-flag / lead-toggle on the detail page)
save through header-gated `X-Requested-With` twins and re-render the affected section fragment — the
same `?fragment=…` fragment the include-inactive filters already swap, plus a new
`special-command-detail :: membersResults` fragment for the member roster. A full server re-render is
exactly what re-syncs every row's `@Version`, the active / role / lead badges and the frequency
ordering, so a second action can no longer 409, and the reorder drops its `location.reload()`.
**announcement** (update / delete), **material-aliases** (create / update / delete), **material
categories** (create / delete) and **admin-settings** (the five-version save) patch their own
row / version inputs in place; settings validation failures and material-category conflicts come back
as `application/problem+json` so the client toasts the exact reason or offers the reload-confirm. The
**uex** three-state loading-dock / auto-load overrides and the terminal hidden toggle patch their
button group (and the terminal UEX-source chip) **deterministically from the clicked action** — these
overrides carry no `@Version`, so no fragment re-render is needed — and the **locations** visibility /
home-location toggles flip server-side off a fresh read and re-render the row's two buttons. **bank**
wipe-reset and **sync-reports** purge keep their type-to-confirm / confirm hurdles and report the
outcome as a toast; **p4k-import** (already AJAX) had its bespoke CSRF reader retired onto `krtCsrf`.
Every classic `POST`→redirect handler stays as the no-JS fallback.

**Enforced by:** lists/pagination e2e (#573) plus the mission-detail (#574), order-detail (#575),
refinery-import (#591), asset-management (#578), bank (#579), promotion (#580), org/members/profile
(#581) and admin-CRUD (#582) twin / fragment / endpoint MVC + e2e tests. **Issues:** the epic children
(#572) through (#591), most recently (#580), (#581) and (#582), the last child. **Code:**
`krt-fetch.js` (`swap`), `missions.js`, `operations.js`, `fragments/pagination.html`,
`mission-detail.html`,
`orders-index.html`, `orders-detail.html`, `refinery-orders-create.html`, `datetime-splitter.js`,
`hangar.html`, `ship-data.html`, `personal-inventory.html`, `personal-inventory-blueprints.html`,
`personal-inventory*.js`, `bank.js`, `bank-account-detail.html`, `bank-manage.html`,
`bank-grants.html`, `promotion-admin-topics.html`, `promotion-admin-rank-requirements.html`,
`promotion-manage.html`, `org-chart.html`, `members.html`, `member-edit.html`, `profile.html`,
`index.html`, and the #582 admin pages — `announcement.html`, `sync-reports.html`, `locations.html`,
`materials.html`, `material-aliases.html`, `admin-settings.html`, `uex.html`,
`fragments/admin-uex.html`, `mission-data.html`, `special-commands.html`,
`special-command-detail.html`, `p4k-import.js` — over `JobOrderPageController`,
`RefineryOrderPageController`, `HangarPageController`, `ShipDataPageController`,
`PersonalInventoryPageController`, `PersonalInventoryBlueprintsPageController`, `BankPageController`,
`BankManagePageController`, `BankGrantsPageController`, `PromotionPageController`,
`OrgChartPageController`, `MemberManagementController`, `ProfileController`, `HomeController`,
`AdminAnnouncementPageController`, `AdminSyncReportsPageController`, `AdminBankPageController`,
`AdminLocationsPageController`, `AdminMaterialsPageController`, `AdminMaterialAliasesPageController`,
`AdminSettingsPageController`, `AdminUexPageController`, `AdminMissionDataPageController` and
`AdminSpecialCommandsPageController`.

### REQ-FE-006 — Navigate-after-AJAX for create / finalize flows that legitimately land elsewhere

Some write flows finish by landing the user on a **different** page — creating an entity navigates
to its detail page or the list, and the refinery detail-page actions (save / store / cancel) redirect
to the refinery-order list. For these flows the no-reload guarantee of REQ-FE-001 applies to the
**failure path**: a client-side validation error or a backend save error keeps the user on the page
with their entered data and shows an inline KRT toast, instead of the classic full reload that
discards a half-filled form. On success the handler deliberately navigates to the server-returned
`{"targetUrl": …}` JSON — the navigation **is** the user's intended outcome, so this is a refinement
of REQ-FE-001, not a violation (there is no in-place reload that would lose work).

The AJAX twin is routed by an `X-Requested-With=XMLHttpRequest` header (more specific than the classic
`@PostMapping`, so Spring dispatches header-bearing requests to it) and submits a `FormData` of the
real `<form>`, which lets the browser serialize the page's dynamic editors (order item lines, refinery
goods / store items) and omit the disabled inactive-mode controls without hand-rolled JSON. The
classic `POST→redirect` handler stays untouched as the no-JS fallback, and the twin reuses the same
DTO-building / backend call as the classic path. Optimistic-lock and other backend errors are
re-emitted as `application/problem+json` (the `propagateBackendError` helper) so the page-local
submit helper can surface them inline.

**Acceptance**

- [ ] A client-side or backend validation/save error on a create / refinery-finalize submit keeps the
  document on the same URL with the entered data intact and shows an inline toast — no full reload.
- [ ] On success the page navigates exactly once to the server-supplied `targetUrl`.
- [ ] With JavaScript disabled the classic form still `POST→redirect`s (the twin is header-gated).

**Enforced by:** create/refinery navigate-after-AJAX MVC tests (`X-Requested-With` twins return
`{targetUrl}` / `400`) · **Issues:** #575 · **Code:** `orders-create.html`, `refinery-orders-create.html`,
`refinery-orders-details.html`, `JobOrderPageController`, `RefineryOrderPageController` (`*Ajax`
twins, `propagateBackendError`).

### REQ-FE-007 — In-place form save with a `{field: message}` validation contract

A form whose server-side validation must stay inline (the mission core-edit `#mission-form`, #589)
saves through a header-gated AJAX twin (`X-Requested-With`) that returns one of three shapes, so the
page never reloads on a save:

- **success** → `200` JSON of the fresh optimistic-lock versions the form must echo back into its
  hidden inputs. The mission twin re-reads the mission after its three section PATCHes to capture the
  server-side `PLANNED→ACTIVE` auto-bump of the schedule version, then returns `{version, coreVersion,
  scheduleVersion, flagsVersion}`; the client writes all four back so a second consecutive save does
  not 409 (`syncVersion` is single-version, so this needs a bespoke handler).
- **validation failure** → `422` with a flat `{field: message}` JSON map whose keys are the bound
  field names and whose values are the messages resolved **exactly as `th:errors`**
  (`messageSource.getMessage(fieldError, locale)`); the client renders them into the matching
  always-present `.field-error[data-error-for="<field>"]` slots (an empty slot is hidden via
  `.field-error:empty`, and the GET render keeps them empty with a `th:text` ternary that never
  evaluates `th:errors` unbound). An unmapped key falls back to a toast so no message is dropped.
- **conflict / backend error** → `problem+json` via `propagateBackendError`, so an `OPTIMISTIC_LOCK`
  code drives the sanctioned reload-confirm and any other code a toast.

The classic `POST→redirect` handler (sharing the patch logic with the twin via a private
`applyMissionUpdate` helper) stays the no-JavaScript fallback, and its inline `th:errors` rendering is
the single source of truth the AJAX message text matches.

`applyMissionUpdate` must **round-trip the schedule datetimes losslessly**: a time field that is
rendered into its hidden input but never re-edited submits the value `formatInstant` produced (a
zoneless local datetime that may carry sub-second precision), and `parseToInstant` must parse it back
to the same instant rather than failing and nulling it. A broken round-trip silently clears
`meetingTime`/`plannedStartTime`/`plannedEndTime` on every save — and because `plannedStartTime` is a
`required` form field, the next page load can no longer submit at all.

**Acceptance**

- [ ] Editing core data saves in place (no navigation) and a second consecutive save does not 409.
- [ ] A `@Valid` failure renders the field message inline with no navigation; fixing + re-saving
  clears it.
- [ ] Saving core data preserves the schedule times the user did not re-edit (no silent nulling).
- [ ] With JavaScript disabled the classic form still `POST→redirect`s (the twin is header-gated).

**Enforced by:** `MissionCoreEditAjaxControllerTest` (four-version re-read, microsecond zoneless
schedule-time round-trip, 422 field map, 409 problem+json, fallback routing) +
`MissionCoreEditInPlaceE2eTest` (in-place save, double-save no-409, inline validation). **Code:**
`mission-detail.html`, `MissionPageController` (`updateMissionAjax`, `applyMissionUpdate`,
`parseToInstant`/`formatInstant`). **Issues:** #589.

### REQ-FE-008 — A bfcache history-restore renders fresh server state

A document restored from the browser's **back/forward cache (bfcache)** must reflect current server
state, not the stale in-memory snapshot the browser replays. A bfcache restore reinstates the DOM and
JS heap captured when the user navigated away — it does **not** re-run the GET — so any
server-rendered aggregate on an overview page (a bank account-card balance, a list count, a status
pill) shows its pre-edit value after the user edits the entity on a forward page and navigates back.
The in-place mutation foundation (REQ-FE-001…007) only keeps the **active** document fresh; it cannot
reach a sibling document the browser later replays. This is the gap behind the reported bank symptom:
deposit on the account-detail page (in-place, correct) → browser back → dashboard card still shows the
old balance until a manual reload.

A single global `pageshow` listener (in
[`common-handlers.js`](../../frontend/src/main/resources/static/js/common-handlers.js), loaded on
every page via `fragments/head.html`) calls `window.location.reload()` exactly when
`event.persisted` is true — the precise signal of a bfcache restore. It cannot loop: a fresh load
fires `pageshow` with `persisted === false`. This is the second sanctioned reload of REQ-FE-001 and is
deliberate (ADR-0013): a full reload, not a fragment swap, because the restored document is an
arbitrary overview with no single swap target, and Spring Security's `no-store` headers do not
reliably suppress bfcache across Chromium / Firefox / WebKit.

**Acceptance**

- [ ] A page restored from bfcache (browser back/forward into a cached document) re-runs its GET and
  renders current server state — a value edited elsewhere in the meantime is reflected without a
  manual reload.
- [ ] The reload fires only on a genuine bfcache restore (`event.persisted`), never on a normal load,
  so it does not loop and adds no navigation to ordinary page views.

**Enforced by:** `BfcacheRefreshE2eTest` (a synthetic `pageshow{persisted:true}` drives a reload that
discards a live-document marker) · **Code:** `common-handlers.js` · **ADR:** ADR-0013

### REQ-FE-009 — Multipart part-count headroom for `FormData` submits

Every in-place AJAX write submits its form as `multipart/form-data` via `FormData` (REQ-FE-002), so
**each form field is its own multipart part** — not only file uploads. Tomcat 11.0.8 lowered the
connector's `maxPartCount` default from 1000 to 10 as a DoS hardening, far below the field count of
the app's larger editors: a refinery order carries ~13 order-level fields plus ~5 per goods row, and
a job order grows with its line items, so a realistic save exceeds 10 parts and fails during
multipart parsing. It surfaces as `MaxUploadSizeExceededException` caused by Tomcat's
`FileCountLimitExceededException` — whose `"attachment"` text is a hardcoded Tomcat constant
(`FileUploadBase.ATTACHMENT`), **not** a form field name, so it must not be read as evidence of an
attachment upload.

The frontend therefore sets `server.tomcat.max-part-count: 1000` — the pre-11.0.8 default the
`FormData` writes were built against, generous for the largest legitimate editor (a refinery order
with ~30 goods is ~165 parts) yet still bounded against a flood; the
`spring.servlet.multipart.max-request-size` cap stays the real volume guard. A part-count or size
breach that still occurs must degrade to a clean, localized **413** — a JSON `{code:
UPLOAD_TOO_LARGE}` body for XHR callers and the `error/error` page otherwise — never the generic
500. Because Spring raises the exception during `DispatcherServlet` multipart resolution (before
handler selection), only the global `GlobalExceptionHandler` `@ControllerAdvice` can intercept it; a
controller-local `@ExceptionHandler` is bypassed.

**Acceptance**

- [ ] A refinery order (or any large `FormData` editor) with more than 10 form fields saves
  successfully — it is not rejected by the connector's part-count limit.
- [ ] A multipart submission that exceeds the configured part-count or size limit returns a
  localized 413 (JSON `UPLOAD_TOO_LARGE` for XHR, the error page otherwise), not a 500.

**Enforced by:** `GlobalExceptionHandlerTest` (the JSON + HTML branches of
`handleMaxUploadSizeExceeded`) · **Config:** `frontend application.yml`
`server.tomcat.max-part-count` · **Code:** `GlobalExceptionHandler.handleMaxUploadSizeExceeded`

## Out of scope

- The per-area conversions themselves (one issue per area, #573–#582) — this spec is the contract
  they each satisfy, not the work list.
- Switching the CSRF token repository to cookie-based, and adopting htmx or app-wide Alpine — all
  explicitly rejected in ADR-0012.
- WebSocket / live-collaboration changes beyond the existing `mission-presence.js`.
- Backend business-logic changes beyond adding JSON proxy endpoints that reuse existing backend
  APIs/DTOs.

## Open questions

- None open. The transitional `MissionSubresource` alias was **removed** in #574; mission-detail now
  calls `krtFetch.write` through a small page-local `krtMissionWrite` wrapper, so `krt-fetch.js`
  carries no page-specific code.
- **Resolved (#574 → #589):** the mission core-edit form (`#mission-form`) is now in-place — it saves
  through the `updateMissionAjax` twin with inline field-error rendering (see REQ-FE-007 below), so
  the whole mission-detail page is reload-free. The classic `POST→redirect` stays the no-JS fallback.
- **Resolved (#575 → #591):** the refinery **screenshot-extract import** carve-out is closed — it now
  swaps the pre-filled create-form fragment in place via the `importExtractAjax` twin (see REQ-FE-005
  above), and `datetime-splitter.js` was made swap-safe in the process. The whole refinery surface is
  now reload-free.

