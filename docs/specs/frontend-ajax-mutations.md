> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-14.
> **Owner area:** FE/UI · **Related ADRs:** ADR-0012

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
**only** sanctioned reload is the optimistic-lock conflict path, where the user explicitly accepts a
reload via `showKrtConfirm`.

**Acceptance**

- [ ] After the action, the URL and document are unchanged and the affected node(s) reflect the new
  state (badges, totals and status cells re-derived in the success handler, not by a reload).
- [ ] No `location.reload()` runs on the success path; the only reload is a user-accepted
  optimistic-lock confirm.

**Enforced by:** per-area Playwright e2e (no-navigation assertion) · **Code:** `krt-fetch.js`,
the converted page handlers · **Issues:** #571, #572

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

**Acceptance**

- [ ] A second consecutive action on the same row/aggregate after a successful write does not
  produce a 409.
- [ ] All related `[data-version]` attributes in the DOM context hold the new version after success.

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
and delivered toggle move onto `krtFetch.write` (per-row patch; a full-amount transfer consumes the
source row, 204, and removes it); and the admin delete-all clears the grouped table via the existing
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
the `.tab-count` aggregates and every trigger button's fresh `data-field-version` re-render atomically,
fixing the shared deactivate/reactivate-modal stale-version trap), and grant create / revoke swap
`grantsMatrix` honouring the active `view` / `accountId` / `userId` filter (#573). The one genuinely
isolated single-row write — a grant capability flag toggle — stays a precise dom-patch (`button.on` +
the row's `data-can-*` + `krtFetch.syncVersion` from the `BankGrantDto` response).

**Enforced by:** lists/pagination e2e (#573) plus mission-detail (#574), order-detail (#575),
refinery-import (#591) and bank (#579) fragment/endpoint MVC + e2e tests · **Issues:** #572 to #575,
#579, #591 · **Code:** `krt-fetch.js` (`swap`), `missions.js`, `operations.js`,
`fragments/pagination.html`, `mission-detail.html`, `orders-index.html`, `orders-detail.html`,
`refinery-orders-create.html`, `datetime-splitter.js`, `bank.js`, `bank-account-detail.html`,
`bank-manage.html`, `bank-grants.html`, `JobOrderPageController`, `RefineryOrderPageController`,
`BankPageController`, `BankManagePageController`, `BankGrantsPageController`.

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

