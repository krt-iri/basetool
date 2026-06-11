> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-10.
> **Owner area:** REFINERY · **Related:** [`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md) (epic #439 — historical plan, frozen 2026-06-10), [`DESIGN_SC_EXTRACTOR.md`](../DESIGN_SC_EXTRACTOR.md), [ADR-0007](../adr/0007-client-side-vlm-screenshot-extraction.md), [ADR-0008](../adr/0008-refinery-extract-json-contract.md), [`api-conventions.md`](api-conventions.md), [`security-and-access.md`](security-and-access.md)

# Refinery screenshot import

## Context & goal

Players document refinery orders as Star Citizen screenshots. Epic #439 turns those
screenshots into pre-filled refinery-order create forms: a desktop tool extracts a
`RefineryExtract` JSON locally (client-side VLM via Ollama, ADR-0007), the user uploads
that JSON, and the backend matches it against master data into a **non-persisted draft**
the user reviews before saving through the unchanged create path.

This spec holds the requirements that are implemented on `main`. Phase 1 (#434, the
backend import endpoint) minted `REQ-REFINERY-001`–`009`, `011` and `012`;
`REQ-REFINERY-010` hardens the shared alias table the import consults (shipped
separately, #517); Phase 2 (#435, the frontend upload + pre-filled review form) added
`REQ-REFINERY-013`–`016`. The desktop extractor (#436, shipped 2026-06-10 as
`basetool-bp-extractor` PR #5) lives in its own repo; its binding desktop-side rules —
the frozen read strategy, the deterministic confidence policy, the resource-safety
guardrails — are recorded there (`CLAUDE.md`,
`docs/refinery-extractor/PHASE0_FINDINGS.md`, the contract test pinning the §5 example),
while the cross-repo contract stays governed by `REQ-REFINERY-001` + ADR-0008 here. The
original master plan is frozen as a historical record in
[`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md).

## Requirements

### REQ-REFINERY-001 — Contract acceptance (v1 envelope)

`POST /api/v1/refinery-orders/import-extract` accepts the frozen `RefineryExtract` JSON
contract (plan §5, ADR-0008) with `schemaVersion == 1` only. A different version is
rejected with HTTP 400 and the localized detail `error.refineryImport.unsupportedSchemaVersion`.
`orders` must be non-empty (bean validation, max 5); only `orders[0]` is processed — a
surplus is flagged `MULTIPLE_ORDERS_TRUNCATED` (INFO). `orders[0].panelType` must be
`SETUP` (case-insensitive); `PROCESSING`/`UNKNOWN` reject with 400 and
`error.refineryImport.unsupportedPanelType`. Defensive caps: ≤ 100 goods per order,
`@Size` limits on all raw strings.

### REQ-REFINERY-002 — Draft only, never persisted

The import endpoint persists **nothing** — no order, no goods, no audit rows. It returns
a `RefineryImportDraftDto` (best-effort `RefineryOrderDto` + issues + counters). Saving
the reviewed draft goes exclusively through the pre-existing
`POST /api/v1/refinery-orders` create path with its full validation and org-unit scoping;
the import feature must not alter that path.

### REQ-REFINERY-003 — Error semantics

Envelope-level problems (REQ-REFINERY-001) are the **only** 400s the import service
raises, always via `BadRequestException` carrying an i18n key the
`GlobalExceptionHandler` resolves. Every content-level problem — unmatched names,
skipped/un-quoted rows, checksum mismatches, out-of-range values — returns HTTP 200 with
a draft plus `ImportIssueDto`s, so the user always sees what was read and why a field is
empty.

### REQ-REFINERY-004 — Material matching algorithm

Raw screen names resolve against master data deterministically, stopping at the first
hit, with **both** sides folded by the shared canonicalizer (lowercase, parentheticals
stripped, qualifier words `raw/ore/refined/pure/r` dropped, non-alphanumerics folded —
`MaterialNameCanonicalizer`, behaviour-identical to the SC-Wiki sync folding):

1. unique canonical-name match,
2. curated `MaterialExternalAlias` lookup (source `REFINERY_SCREEN`, case-insensitive),
3. unique suffix/contains match for game-UI-truncated names (≥ 5 canonical chars),
4. fuzzy fallback via the reused `BlueprintFuzzyMatcher`; a hit at or above the
   configurable accept threshold (`krt.refinery-import.fuzzy-accept-threshold`,
   default 0.9) is applied **and** flagged `LOW_CONFIDENCE_MATERIAL`; below the
   threshold the row stays unmatched with ranked `suggestions` attached.

The candidate set mirrors the create path's input gate exactly: visible materials with
`type == RAW` or `isManualRawMaterial == true`. Materials are never auto-created from
external names. A matched input's `outputMaterial` derives from the admin-curated
`Material.refinedMaterial` link; a missing link is `NO_REFINED_MATERIAL` (INFO), not an
error.

### REQ-REFINERY-005 — Row skip rules & un-quoted state

A source row never becomes a draft good when (checked in this order): the REFINE toggle
is off (`SKIPPED_REFINE_OFF`, INFO), the YIELD cell is un-quoted / `outputQuantity ==
null` (`UNQUOTED_ROW`, WARNING — fix is re-capturing after GET QUOTE), or a quantity is
below 1 (`SKIPPED_ZERO_QTY`, WARNING). Skipped rows are counted in `rowsSkipped` and
`goodsTotal`. When the producer marks the capture un-quoted (`quoted == false`) **or**
no row carries an `outputQuantity`, the draft additionally carries `UNQUOTED_ORDER`
(BLOCKING). Duplicate material rows (same ore, different qualities) are normal and must
be preserved, ordered by the stitched `rowIndex`.

### REQ-REFINERY-006 — Quality handling

A `null` quality defaults to `0` (existing create-form convention). An out-of-range
quality (outside 0–1000) is kept un-clamped in the draft and flagged
`OUT_OF_RANGE_QUALITY` (WARNING): draftable but never savable — the entity constraint
forces correction in the review form.

### REQ-REFINERY-007 — Header-total checksum

When the extract carries `rawToRefineTotal`, the backend applies the frozen Phase-0
checksum (one-sided; extractor repo `PHASE0_FINDINGS.md` §7): `SUM_MISMATCH` (WARNING)
is flagged only when the sum of the **refine-ON** row input quantities exceeds
`rawToRefineTotal` by more than a ±1-per-row display-rounding tolerance, or when a
single row alone exceeds it by more than 1 — both indicate a mis-read quantity or a
duplicated capture. A shortfall is never flagged: the materials list is a scrolling
~6-row viewport, so scrolled-out rows legitimately reduce the visible sum.
`rawInManifestTotal` is **never** validated — its composition is not reliably
reconstructible from a single frame (golden-set order a4 counted some refine-OFF rows
but not others; a 2026-06 field sample excluded the inert row entirely).

*Amended 2026-06-11:* the original v1 hypothesis (`rawInManifestTotal` = sum of all
rows, hard equality on both totals) was refuted by the Phase 0 (#433) verification and
a field sample; the rule above mirrors the extractor's frozen `Validation` semantics.

### REQ-REFINERY-008 — Order-level resolution

`rawLocationName` resolves by unique canonical name against the refinery-equipped
locations only (`LocationRepository.findLocationsWithRefinery()` — the create-form
picker source); `rawMethodName` resolves case-insensitively against
`refining_method.name`. An unresolved or absent value leaves the draft field `null` and
adds `UNRESOLVED_LOCATION` / `UNRESOLVED_METHOD` (WARNING) — the normal case for
pre-cropped panel input, which never contains the terminal header. `expenses` and
`durationMinutes` are copied verbatim; `status` defaults to `OPEN`; the owner defaults
to the uploading user; `mission`, `startedAt`, `otherExpenses`, `oreSales` and the
org-unit stamping stay with the create flow.

### REQ-REFINERY-009 — Issue model (cross-module contract)

`ImportIssueDto(field, rawValue, code, severity, confidence, suggestions)` is the wire
contract the review surfaces render. `ImportIssueCode` and `ImportIssueSeverity` enum
constants are frozen identifiers — the frontend translates codes via
`refineryImport.issue.<CODE>` message keys, so renaming a constant is a breaking change.
Field paths: draft-row issues use the index in `draft.order.goods[]` plus sub-field
(`goods[2].inputMaterial`); skipped-row issues use the bare on-screen reference
(`goods[<rowIndex>]`); order-level issues use plain field names. `confidence` carries
the fuzzy score for `LOW_CONFIDENCE_MATERIAL`, otherwise the row's derived read
confidence from the contract (never re-derived). `suggestions`
(`ImportSuggestionDto(id, name, score)`, ranked) accompany every
`UNMATCHED_MATERIAL`/`LOW_CONFIDENCE_MATERIAL` issue when candidates score above the
suggestion floor.

### REQ-REFINERY-010 — Material-alias uniqueness is case-insensitive

At most one `material_external_alias` row may exist per
`(source_system, LOWER(external_name))`. The uniqueness rule must match the resolution
lookup (`findBySourceSystemAndExternalNameIgnoreCase`), which folds case because external
systems drift casing across patch versions (the refinery screen shows `"STILERON (ORE)"`
where the Wiki writes `"Stileron (Ore)"`).

*Why:* the original V108 constraint was case-sensitive while the lookup was not. Two rows
differing only in case could legally coexist, making the `Optional`-returning lookup throw
`IncorrectResultSizeDataAccessException` → HTTP 500 on **every** import/sync touching that
name. V146 de-duplicated existing case-variant rows (oldest row per group survives) and
replaced the constraint with the functional unique index
`uq_material_external_alias_source_lower_name`.

**Acceptance**

- [x] The DB rejects a second alias whose `(source_system, external_name)` differs from an
  existing row only in case (V146 unique index on `(source_system, LOWER(external_name))`).
- [x] `MaterialExternalAliasService.create` and `.update` detect a case-insensitive
  duplicate pre-emptively and raise `DuplicateEntityException` → HTTP 409 (clean conflict
  instead of a generic DB error); recasing a row's *own* name remains allowed.
- [x] `resolveMaterialByAlias` can never observe two candidate rows, so the
  `IncorrectResultSizeDataAccessException` failure mode is structurally impossible.

**Enforced by:** `MaterialExternalAliasServiceTest`, `DatabaseIndexMigrationTest` ·
**Code:** `MaterialExternalAliasService`, `MaterialExternalAliasRepository`,
`V146__make_material_alias_uniqueness_case_insensitive.sql` · **Issues:** epic #439

### REQ-REFINERY-011 — Security

The import endpoint requires authentication (`@PreAuthorize("isAuthenticated()")`), no
elevated role — any member may build a draft. The draft's owner reference defaults to
the caller. Because nothing is persisted, org-unit scoping is not consulted here; it
applies unchanged when the reviewed draft is saved through the create path
(REQ-REFINERY-002).

### REQ-REFINERY-012 — Alias curation

Refinery-screen aliases live in the existing `material_external_alias` table under the
dedicated source `REFINERY_SCREEN` (V148 widened the V108 CHECK constraint). Admins
curate them at `/admin/material-aliases`. An alias whose target material fails the
REQ-REFINERY-004 candidate gate is ignored by the import (logged, falls through to the
next matching stage) — curation cannot bypass the create-path mirror. Resolution is
deterministic: REQ-REFINERY-010 enforces case-insensitive uniqueness DB-side and the
service rejects case-variant duplicates with a clean 409. No aliases are seeded by
migration — entries come from golden-set verification (#433) and live curation.

### REQ-REFINERY-013 — Frontend upload seam

The refinery create page carries the import control: a hidden `<input type="file">`
behind a styled trigger button (the established KRT pattern) that submits a regular
multipart form to `POST /refinery-orders/import` (authenticated). The proxy verifies the
upload parses as a JSON **object** locally (cheap pre-check with a friendly localized
error, sanity-capped at 2 MB — a real extract is a few KB), relays the parsed document
as an `application/json` body to `POST /api/v1/refinery-orders/import-extract`, and
never persists anything itself. The frontend mirrors the backend draft DTOs
field-for-field (mirror-DTO rule), including the `ImportIssueCode` /
`ImportIssueSeverity` enums.

### REQ-REFINERY-014 — Server-side pre-fill via flash attributes

The pre-fill reuses the create page's existing flash-attribute mechanism — the GET
handler already prefers a flashed `refineryOrderForm` over a fresh one — so no new
client-side fill logic exists. Mapping rules: nested DTO ids into the form's id fields,
`durationMinutes` split into the hours/minutes inputs, money fields defaulted to `0`,
`status` defaulted to `OPEN`, `startedAt` left empty (the create flow defaults it to
"now" at save time). An all-skipped draft keeps the form's single seeded empty goods row
so the template's row-clone JS keeps working. Saving still goes exclusively through the
unchanged create POST with full validation (REQ-REFINERY-002).

### REQ-REFINERY-015 — Review rendering

The redirected create page renders the draft findings without re-deriving anything:

- a summary banner counts the match result (`{matched} of {total} rows, {skipped}
  skipped`) and lists every finding that has no rendered form row (order-level fields,
  skipped/un-quoted source rows), each translated via `refineryImport.issue.<CODE>` with
  the verbatim raw read appended;
- findings anchored to a draft row (`goods[<draftIndex>].<subField>`) render as inline
  flags on that goods row; flagged rows get a 3 px left status bar;
- confidence renders as percent text in the **accessible tints** (`--color-*-text`,
  REQ-UI-006) plus a square dot in the canonical hue, coloured ≥ 90 % success / 75–90 %
  warning / < 75 % danger — the value is always the backend-supplied confidence;
- `UNMATCHED_MATERIAL` / `LOW_CONFIDENCE_MATERIAL` flags render the ranked
  `suggestions` as one-click chips that assign the candidate to the row's material
  select (dispatching the normal change event so output display and yield badge
  update); an unmatched row's empty required select forces completion before save.

The create form has no Refine column (drafted rows are refine-ON by definition —
refine-off rows are skipped backend-side per REQ-REFINERY-005), so no toggle/switch
component was introduced; the design-system component budget is unchanged.

### REQ-REFINERY-016 — Error and empty states

All import feedback is KRT-styled inline alerts (no native dialogs, REQ-UI-008): a
non-JSON / non-object / oversized upload fails locally with
`refineryImport.error.invalidFile` (uploads beyond Spring's multipart cap are caught by
a controller-local `MaxUploadSizeExceededException` handler and yield the same inline
error instead of the generic error page); an envelope-level backend reject (wrong
`schemaVersion`, non-SETUP panel) surfaces the backend's localized problem detail
verbatim — which requires the frontend `WebClient` to relay the user's resolved locale
as `Accept-Language` on every backend call (`UserLocaleRelayFilter`; without the header
the backend localizes in its container-default locale, violating the i18n rule); an
unexpected relay failure falls back to `refineryImport.error.failed`; a draft with zero
matched rows adds an explicit zero-matches hint to the banner; a fully un-quoted order
surfaces its `UNQUOTED_ORDER` finding danger-tinted. All strings live in
`refineryImport.*` keys in the three frontend bundles (DE default + EN parity).

## Traceability

- `RefineryImportServiceTest`, `RefineryImportControllerTest`,
  `RefineryExtractDtoJsonTest`, `MaterialNameCanonicalizerTest` cover
  REQ-REFINERY-001–009 and 011 (test names reference the behaviour, not the id; this
  table is the mapping).
- `MaterialExternalAliasServiceTest` + `DatabaseIndexMigrationTest` cover
  REQ-REFINERY-010 (see its embedded acceptance list).
- V148 migration + `MaterialExternalAliasSource.REFINERY_SCREEN` + the
  `/admin/material-aliases` option cover REQ-REFINERY-012.
- `RefineryImportProxyControllerTest` (relay, form mapping, error branches),
  `RefineryOrderCreateImportRenderTest` (full Thymeleaf render with flags, chips and
  banner) and the `RefineryImportE2eTest` file-upload flow
  ([UC-24](../e2e-test/UC-24-refinery-import-extract.md)) cover REQ-REFINERY-013–016.

## Out of scope

- The desktop extractor's internals ([#436](https://github.com/krt-iri/basetool/issues/436),
  shipped) — they live in the `basetool-bp-extractor` repo; this spec only governs the
  cross-repo contract and the basetool-side import behaviour.
- The deferred phases 4 (#437, direct upload) and 5 (#438, log hints) — deferred by owner
  decision; their GitHub issues govern them if they are ever revived.
- `blueprint_external_alias` — the blueprint import keeps its own alias table and matching
  rules, specced in [`blueprint-import-name-matching.md`](blueprint-import-name-matching.md).

