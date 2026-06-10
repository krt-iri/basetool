> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-10.
> **Owner area:** REFINERY · **Related:** [`REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md) (epic #439 forward plan), [`DESIGN_SC_EXTRACTOR.md`](../DESIGN_SC_EXTRACTOR.md), [ADR-0007](../adr/0007-client-side-vlm-screenshot-extraction.md), [ADR-0008](../adr/0008-refinery-extract-json-contract.md), [`api-conventions.md`](api-conventions.md), [`security-and-access.md`](security-and-access.md)

# Refinery screenshot import

## Context & goal

Players document refinery orders as Star Citizen screenshots. Epic #439 turns those
screenshots into pre-filled refinery-order create forms: a desktop tool extracts a
`RefineryExtract` JSON locally (client-side VLM via Ollama, ADR-0007), the user uploads
that JSON, and the backend matches it against master data into a **non-persisted draft**
the user reviews before saving through the unchanged create path.

This spec holds the requirements that are implemented on `main`. Phase 1 (#434, the
backend import endpoint) minted `REQ-REFINERY-001`–`011`; later phases (frontend upload
UI #435, desktop extractor #436) add their requirements here when they ship. The full
forward plan, including not-yet-built phases, lives in
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

When the extract carries the panel-header totals, the backend reconciles them and flags
a difference as `SUM_MISMATCH` (WARNING, "a scrolled screenshot may be missing"):
`rawInManifestTotal` against the sum of **all** row input quantities and
`rawToRefineTotal` against the sum of **refine-ON** row input quantities. These v1
semantics are the golden-set hypothesis; Phase 0 (#433) freezes the exact rule and this
requirement is amended if the verification disagrees.

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

### REQ-REFINERY-010 — Alias curation

Refinery-screen aliases live in the existing `material_external_alias` table under the
dedicated source `REFINERY_SCREEN` (V146 widened the V108 CHECK constraint). Admins
curate them at `/admin/material-aliases`; the `(source_system, external_name)` unique
constraint keeps resolution deterministic. No aliases are seeded by migration — entries
come from golden-set verification (#433) and live curation.

### REQ-REFINERY-011 — Security

The import endpoint requires authentication (`@PreAuthorize("isAuthenticated()")`), no
elevated role — any member may build a draft. The draft's owner reference defaults to
the caller. Because nothing is persisted, org-unit scoping is not consulted here; it
applies unchanged when the reviewed draft is saved through the create path
(REQ-REFINERY-002).

## Traceability

- `RefineryImportServiceTest`, `RefineryImportControllerTest`,
  `RefineryExtractDtoJsonTest`, `MaterialNameCanonicalizerTest` cover
  REQ-REFINERY-001–009 and 011 (test names reference the behaviour, not the id; this
  table is the mapping).
- V146 migration + `MaterialExternalAliasSource.REFINERY_SCREEN` + the
  `/admin/material-aliases` option cover REQ-REFINERY-010.

