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

**Enforced by:** lists/pagination e2e (#573) plus mission-detail (#574) and order-detail (#575)
fragment/endpoint MVC tests · **Issues:** #572 to #575 · **Code:** `krt-fetch.js` (`swap`),
`missions.js`, `operations.js`, `fragments/pagination.html`, `mission-detail.html`,
`orders-index.html`, `orders-detail.html`, `JobOrderPageController`.

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
- **Known carve-out (#574 → #589):** the mission core-edit form (`#mission-form`: name/description/
  status/operation/schedule) still submits classic `POST→redirect`. It fans out into three section
  PATCHes (core/schedule/flags) with server-side `@Valid` field-error rendering; converting it in
  place needs a JSON field-error contract and is tracked as the follow-up issue **#589** so the
  well-tested validation UX is not regressed. Every other mission-detail write is in-place.

