# Refinery Screenshot Import — Master Implementation Plan

> **Status:** Approved as the plan; **implementation NOT yet started**. Do not write feature code until the repository owner explicitly approves the phase you are about to build.
> **GitHub epic:** [krt-iri/basetool#439](https://github.com/krt-iri/basetool/issues/439) · **Sub-issues:** #433 (Phase 0), #434 (Phase 1), #435 (Phase 2), #436 (Phase 3), #437 (Phase 4 — deferred), #438 (Phase 5 — deferred).
> **Last updated:** 2026-06-05.
> **Example screenshots:** the owner's example refinery-order SETUP screenshots are at <https://nc.greluc.me/s/bbzFYL4PT4jkBKK> — the source material for the Phase 0 golden set and the test fixtures of Phases 1–3.
> **Design (binding):** the extractor GUI **and** the frontend review surface follow the Claude Design spec in [`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) — canonical prototype/bundle at <https://api.anthropic.com/v1/design/h/mM6v29vZctN6kiBsF62_eA>, with an **offline-capable single-file mirror** vendored at [`docs/design/basetool-sc-extractor.offline.html`](design/basetool-sc-extractor.offline.html) (open in any browser, no network). Frozen UI decisions: Top-Tabs navigation, comfortable density, honeycomb texture on, confidence as percent + status dot, custom KRT title bar, DE default with full EN parity. Build the rebuilt tool **screen-for-screen** against the prototype.

---

## 0. How to use this document (read first if you are the implementing AI)

This document is written to be executed by an AI coding agent, one phase per session.

1. **Pick exactly one phase** (the owner tells you which). Read this whole document once, then work only that phase's section plus the cross-cutting rules in §8.
2. **Before touching code, read the target repo's `CLAUDE.md`** (basetool) or `CLAUDE.md` (basetool-sc-extractor). Those rules **override** anything here if they ever conflict — but flag the conflict in your summary.
3. **Honour the frozen contract in §5 verbatim.** The `RefineryExtract` JSON is the single hand-off between the desktop tool (Phase 3) and the backend (Phase 1). Changing it breaks the other phase. If you believe it must change, stop and raise it with the owner first; do not silently diverge.
4. **Every phase ships with tests** (project hard rule) and must pass the exact build/lint/test commands listed in that phase's "Definition of Done".
5. **Do not commit, branch, or push unless the owner explicitly asks.** "Verify" means build + test, not git operations. When you do commit, every commit needs a DCO `Signed-off-by:` trailer (`git commit -s`) **and** a `Co-Authored-By: Claude <model> <noreply@anthropic.com>` trailer. All Git/GitHub text is English.
6. **Scope discipline:** build only what the phase section lists under "Deliverables". Anything under "Out of scope (this phase)" is deliberately deferred — do not pull it forward.

---

## 1. Goal & user story

**As a** Star Citizen refinery operator in the org, **I want** the basetool to read my in-game **refinery SETUP screenshots** and pre-fill a new Refinery Order — **above all the materials table** (material, quality, input qty, projected yield, refine on/off), plus location, refining method, total cost and processing time — **so that** I do not have to retype a 10–20 row order by hand.

**Acceptance from the user's point of view:**
- I run a small desktop app on my own PC, point it at my screenshot(s), and get a JSON file.
- I open the basetool refinery page, upload that JSON, and the create form is **already filled in**.
- Anything the tool could not read or match is **clearly flagged** so I can complete it by hand.
- I review, fix if needed, and save. **Nothing is saved without my confirmation.**

---

## 2. Hard constraints (non-negotiable)

| # | Constraint | Consequence for the design |
|---|---|---|
| C1 | **Only free / open-source tools.** No paid API, no API key, no third-party SaaS. | Vision reading runs through a **local** VLM (Ollama). |
| C2 | **The production server must not be overloaded.** It is a single Hetzner VM: **4 vCPU / 8 GB RAM / 160 GB disk, no GPU**. The basetool must stay usable and the disk must not fill. | The VM does **no** image handling and **no** inference. It only ingests a small JSON and matches against master data. No screenshot is ever uploaded to or stored on the server. |
| C3 | **v1 = manual JSON upload through the frontend only.** There is currently **no** way for the desktop tool to authenticate to and push data into the backend directly. | The desktop tool writes a JSON **file**; the user uploads it via the existing frontend, exactly like the hangar ship-list import. **No desktop→backend channel in v1** (that is Phase 4, deferred). |
| C4 | **Screenshots can be any resolution 1080p → 8K, including ultrawide 5K (21:9 / 32:9).** | A fixed-pixel crop is impossible. The desktop pipeline is resolution-agnostic (§9 Phase 3). |
| C5 | **Newest/best free VLM via Ollama.** | Default model `qwen3-vl` (configurable), low-VRAM fallback identified in Phase 0. |
| C6 | **Client-side resource safety.** Running the VLM while SC is active is unsafe (GPU/RAM contention → SC stutter/crash or very slow extraction). The user must be warned of minimum PC requirements and warned when below them. | "Close SC" guidance + SC-process detection + preflight hardware check + throttling (§9 Phase 3, §6 resource safety). |

---

## 3. Resolved design decisions (owner-answered 2026-06-05 — do not re-open)

1. **Desktop tool placement — integrate & rebrand.** Build the refinery extractor **inside the existing `basetool-bp-extractor` repo, rebranded to `basetool-sc-extractor`**. A **central launcher GUI** lets the user choose a workflow: the existing **Blueprint** extraction or the new **Refinery** extraction. The repo's scope discipline widens from "blueprints only" to a focused **multi-workflow SC extractor**. *(Rejected: a separate repo; a shared-core monorepo.)*
2. **Panel types in v1 — SETUP only.** v1 reads only the refinement **SETUP** screen. The **PROCESSING** screen and "update an existing order from a PROCESSING screenshot" are deferred. The contract keeps the `panelType` field for forward-compatibility, but the v1 producer emits `SETUP` only and the v1 backend accepts `SETUP` only.
3. **SC client language — English only.** Master-data matching targets the **English** SC client; the golden set is English. No German synonym table in v1. *(Frontend UI i18n DE/EN is a separate, unchanged requirement.)*
4. **Ollama — guided prerequisite, not bundled.** The user installs Ollama themselves (documented). On launch the app verifies Ollama is reachable and the configured model is present, and offers to fetch it via `ollama pull` with a progress indicator. *(Rejected: auto-installing Ollama; doc-only hard fail.)*
5. **GUI design — frozen, build to the prototype.** The desktop rebuild **and** the frontend review surface follow the binding Claude Design spec ([`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) + the prototype at <https://api.anthropic.com/v1/design/h/mM6v29vZctN6kiBsF62_eA>, mirrored **offline** as the vendored single file [`docs/design/basetool-sc-extractor.offline.html`](design/basetool-sc-extractor.offline.html)). Locked: **Top-Tabs** navigation (Start · Blueprints · Refinery + per-workflow stepper), **comfortable** density (44 px targets), **honeycomb** texture (~0.32 opacity), **confidence as percent + status dot**, custom **KRT title bar**, **German default with full EN parity**. Reuse the DAS KARTELL / KRT tokens — do not invent any. *(Rejected for v1: rail/launcher nav models; bar/flag confidence styles — kept in the prototype for reference only.)*

---

## 4. Architecture overview

```
 ┌─ DESKTOP (user's PC, has a GPU) ──────────────────────────────┐
 │ basetool-sc-extractor  ──launcher──►  [Blueprint] / [Refinery] │
 │                                                                │
 │ Refinery workflow:                                             │
 │   1. user selects screenshot(s)  (1 selection = 1 order)       │
 │   2. preflight: hardware check + "is SC running?" warning      │
 │   3. local VLM via Ollama (qwen3-vl)                           │
 │      Locate → Normalize → Read   (per image)                   │
 │   4. stitch + dedupe rows across the scrolled screenshots      │
 │   5. write  RefineryExtract.json   to a user-chosen path       │
 └───────────────────────────────┬───────────────────────────────┘
                                  │  user manually uploads the JSON file
                                  │  (browser → frontend; ~KB)
 ┌─ BROWSER ──────────────────────▼───────────────────────────────┐
 │ basetool frontend  /refinery-orders/import  (multipart upload)  │
 └───────────────────────────────┬───────────────────────────────┘
                                  │  WebClient relay (JSON body)
 ┌─ BASETOOL BACKEND (Hetzner VM — light work only) ──────────────▼┐
 │ POST /api/v1/refinery-orders/import-extract   (application/json) │
 │   • parse + validate RefineryExtract                            │
 │   • fuzzy-match Material / Location / RefiningMethod → master    │
 │   • derive outputMaterial via Material.refinedMaterial          │
 │   • build a best-effort RefineryOrderDto draft + issue list     │
 │   ► returns RefineryImportDraftDto (NOT persisted)              │
 └───────────────────────────────┬───────────────────────────────┘
                                  │  draft
 ┌─ BROWSER ──────────────────────▼───────────────────────────────┐
 │ existing refinery create form, PRE-FILLED + unmatched flagged   │
 │ user reviews / fixes / confirms                                 │
 │   ► POST /api/v1/refinery-orders   (the existing create path)   │
 └─────────────────────────────────────────────────────────────────┘
```

**Why this satisfies the constraints:** all vision inference is on the player's GPU (C1, C2); the server only parses a tiny JSON and does DB lookups (C2 — negligible CPU/disk, no image storage); the hand-off is a file the user uploads manually (C3).

---

## 5. The frozen contract — `RefineryExtract` JSON (v1)

This is the **single** integration point. Phase 1 (backend) and Phase 3 (desktop) both implement it; Phase 2 (frontend) only relays it. **Freeze it before 1/2/3 run in parallel.**

```jsonc
{
  "schemaVersion": 1,                     // integer; bump on any breaking shape change
  "tool": "basetool-sc-extractor",        // producer name (provenance)
  "toolVersion": "1.4.0",                 // producer version (provenance)
  "model": "qwen3-vl:8b",                 // which VLM produced this (provenance)
  "generatedAt": "2026-06-05T20:00:00Z",  // UTC ISO-8601 instant
  "clientLanguage": "en",                 // v1 always "en" (see decision 3)
  "orders": [                             // v1 producer emits exactly ONE order
    {
      "panelType": "SETUP",               // SETUP | PROCESSING | UNKNOWN; v1 accepts SETUP only
      "layoutConfidence": 0.92,           // 0..1, model's confidence it parsed the panel layout
      "rawLocationName": "LEVSKI",         // verbatim as read; nullable
      "rawMethodName": "FERRON EXCHANGE",  // verbatim as read; nullable
      "expenses": 48928.00,               // aUEC total cost; nullable
      "durationMinutes": 1258,            // processing time in minutes (from "20h 58m"); nullable
      "totalYieldScu": null,              // PROCESSING only; null on SETUP
      "sourceImages": [
        { "name": "frame_213823.png", "width": 3840, "height": 2160, "cropMode": "vlm" }
      ],
      "goods": [
        {
          "rawMaterialName": "LINDINIUM (ORE)", // verbatim, including any "(ORE)" suffix
          "quality": 618,                 // integer; SC QUALITY column; expected 0..1000
          "inputQuantity": 957,           // integer ≥ 0; SC QTY column
          "outputQuantity": 448,          // integer ≥ 0; SC YIELD column (projected)
          "refine": true,                 // SC REFINE toggle (true=ON, false=OFF/inert)
          "confidence": 0.95,             // 0..1, per-row read confidence
          "sourceImage": "frame_213823.png"
        }
      ]
    }
  ]
}
```

**Field rules the backend enforces (Phase 1):**
- `schemaVersion` must equal `1` → else `400` with a clear message.
- `orders` must be non-empty. v1 processes `orders[0]`; if `orders.size() > 1`, process the first and add an `INFO` issue `MULTIPLE_ORDERS_TRUNCATED`.
- `panelType` must be `SETUP` → a `PROCESSING`/`UNKNOWN` order yields a `400` (or a draft with a blocking issue) in v1.
- A good with `refine == false` **or** `outputQuantity < 1` **or** `inputQuantity < 1` is **not** added to the draft as a `RefineryGoodDto` (it would violate `@Min(1)`); instead it is reported as a `SKIPPED_REFINE_OFF` / `SKIPPED_ZERO_QTY` issue so the user sees it was intentionally dropped.
- `quality` outside `0..1000` → clamp is **not** applied; keep the value but add an `OUT_OF_RANGE_QUALITY` warning so the user notices a likely mis-read.

---

## 6. Star Citizen refinement SETUP screen — reference

The REFINEMENT CENTER **SETUP** tab (where an order is placed). Columns, left→right:

| Column | Meaning | Contract field |
|---|---|---|
| **MATERIALS SELECTED** | the raw ore, e.g. `LINDINIUM (ORE)`; an `INERT MATERIALS` row aggregates non-refinable slag | `goods[].rawMaterialName` |
| **QUALITY** | composition/quality figure | `goods[].quality` |
| **QTY** | raw input quantity | `goods[].inputQuantity` |
| **YIELD** | projected refined output | `goods[].outputQuantity` |
| **REFINE** | per-row ON/OFF toggle (inert rows are OFF) | `goods[].refine` |

Header/footer fields: refinery **location** (e.g. `LEVSKI`), **method** (e.g. `FERRON EXCHANGE`), **TOTAL COST** (aUEC), **PROCESSING TIME** (e.g. `20h 58m`).

**Reference rows** (English client, from the owner's example "Auftrag 1"; full screenshot set at <https://nc.greluc.me/s/bbzFYL4PT4jkBKK>):
```
LINDINIUM (ORE)   QUALITY 618   QTY 957    YIELD 448   REFINE ON
TUNGSTEN (ORE)    QUALITY 530   QTY 1431   YIELD 695   REFINE ON
INERT MATERIALS   QUALITY 0     QTY 5449   YIELD 0     REFINE OFF   ← reported as skipped, not a good
```

One order frequently spans **several scrolled screenshots**; the desktop tool stitches and dedupes the rows into one `order.goods[]` (§9 Phase 3). **Phase 0 verifies the exact column semantics against the golden set** (e.g. whether QUALITY is a 0–1000 grade) and freezes the prompt accordingly.

---

## 7. Master-data mapping & matching (backend, Phase 1)

### 7.1 Order-level mapping

| Contract | basetool field (`RefineryOrderDto`) | How |
|---|---|---|
| `rawLocationName` | `location` (`LocationDto`) | match a `Location` **that has a refinery**, by normalized name; unresolved → `UNRESOLVED_LOCATION` issue, `location` left null |
| `rawMethodName` | `refiningMethod` (`RefiningMethodDto`) | `RefiningMethodRepository.findByName` (add a case-insensitive variant); unresolved → `UNRESOLVED_METHOD` issue |
| `expenses` | `expenses` (`Double`, `@PositiveOrZero @DecimalMax 1e9`) | copy; null stays null |
| `durationMinutes` | `durationMinutes` (`Long`, `@PositiveOrZero`) | copy; null stays null |
| — | `status` (`String`) | default to the open/not-started constant in `RefineryOrderStatus` (e.g. `OPEN`) |
| — | `owner`, `startedAt`, `otherExpenses`, `oreSales`, `mission`, `owningOrgUnitId` | **not** filled from the screenshot — left for the user/normal create flow (see §7.4) |

### 7.2 Per-good mapping (`RefineryGoodDto`)

| Contract | DTO field | How |
|---|---|---|
| `rawMaterialName` | `inputMaterial` (`MaterialDto`, `@NotNull`) | normalize → match a `Material` (§7.3); unresolved → `UNMATCHED_MATERIAL` issue, row kept with null material so the user can pick it |
| (derived) | `outputMaterial` (`MaterialDto`, nullable) | `matchedInputMaterial.getRefinedMaterial()` if present; else null + `NO_REFINED_MATERIAL` info |
| `inputQuantity` | `inputQuantity` (`Integer`, `@NotNull @Min(1)`) | copy |
| `outputQuantity` | `outputQuantity` (`Integer`, `@NotNull @Min(1)`) | copy |
| `quality` | `quality` (`Integer`, entity range 0..1000) | copy; out-of-range → warning (§5) |
| — | `yieldBonusPercent` | leave null — it is a read-only UEX enrichment the backend fills on read, never on import |

### 7.3 Material matching algorithm (deterministic, ordered; stop at first hit)

Normalize the raw name first: uppercase-fold for comparison, **strip a trailing parenthetical** like `(ORE)` / `(RAW)`, collapse repeated whitespace, trim.

1. Exact `MaterialRepository.findByNameIgnoreCase(normalized)`.
2. Exact match on `code` / `slug` / `scwikiKey` (add slim repository finders as needed).
3. **Alias table** lookup (a small curated `Map<String,String>` of known SC-screen → master-data names; seed it from the golden set; keep it in code or a resource file, English only).
4. Fuzzy fallback: normalized Levenshtein / token-set ratio against the set of **visible, refinable** material names (`isVisible == true`, prefer `isRaw == 1` / `isRefinable == 1` / `isManualRawMaterial`); accept only above a configurable threshold (default ~0.9) and record the score; otherwise leave unmatched.

Never silently accept a low-confidence fuzzy match — flag it (`LOW_CONFIDENCE_MATERIAL`) so the review step surfaces it.

### 7.4 What stays manual (always flagged "please complete")

`owner` (defaults to the uploading user), `mission`, `otherExpenses` / `oreSales` (profit tracking — not in the SC UI), exact `startedAt` (defaults to now), storage/job-order links, and **any field/row the VLM read with low confidence or whose name did not match master data**. The draft's `issues[]` enumerates every one of these so the frontend can highlight them.

### 7.5 Draft DTOs (backend, new)

- `RefineryImportDraftDto(RefineryOrderDto order, List<ImportIssueDto> issues, int goodsMatched, int goodsTotal, int rowsSkipped)` — the order is a **best-effort, NOT persisted** pre-fill (nulls where unmatched).
- `ImportIssueDto(String field, String rawValue, ImportIssueCode code, ImportIssueSeverity severity, Double confidence)` where:
  - `field` is a dotted path, e.g. `goods[0].inputMaterial`, `location`, `refiningMethod`.
  - `ImportIssueCode` ∈ `{UNMATCHED_MATERIAL, LOW_CONFIDENCE_MATERIAL, NO_REFINED_MATERIAL, OUT_OF_RANGE_QUALITY, UNRESOLVED_LOCATION, UNRESOLVED_METHOD, SKIPPED_REFINE_OFF, SKIPPED_ZERO_QTY, MULTIPLE_ORDERS_TRUNCATED, UNSUPPORTED_PANEL_TYPE}`.
  - `ImportIssueSeverity` ∈ `{BLOCKING, WARNING, INFO}` (BLOCKING = cannot pre-fill a required field, e.g. unsupported panel type).

---

## 8. Cross-cutting engineering rules (apply to every basetool phase)

These come from the basetool `CLAUDE.md`; the build **fails** if you break them.

- **DTOs only at controller boundaries** — never expose JPA entities; DTOs are records with Jakarta validation; use a **MapStruct** mapper for Entity↔DTO. **`@Valid`** on every write `@RequestBody`.
- **Backend DTO change ⇒ frontend mirror in the same change.** Any new/renamed/removed field on a backend boundary DTO needs the matching frontend mirror record + template update in the same commit (the build pipeline does not catch the asymmetry; it 500s at render time in prod).
- **Security/scope** — every `@RestController` carries `@PreAuthorize`; controllers must not return entities; staffel-scoped reads/writes go through `OwnerScopeService`. Refinery is a **strict-staffel** aggregate. The import endpoint is authenticated (`isAuthenticated()`); creating the order still goes through the existing, already-scoped create path.
- **Flyway** owns the schema (`V<n>__*.sql`); `ddl-auto=validate` everywhere. This feature **needs no new tables** if matching reuses existing master data (confirm before adding any migration). If you do add one, fetch `main` first and pick `max(version)+1` to avoid a merge collision, and read `backend/.../db/migration/README.md`.
- **i18n** — every user-visible string comes from `messages.properties` / `_de` / `_en`. New keys under `refineryImport.*` and `admin.refineryImport.*`. German umlauts in `.properties` are `\uXXXX`; in Markdown they are literal UTF-8.
- **Errors** — RFC 7807 `application/problem+json` via `GlobalExceptionHandler`; validation errors add the `errors` map. Do not hardcode problem-type URIs.
- **API docs** — SpringDoc `@Operation` / `@ApiResponses` on the new endpoint; keep `backend/src/main/resources/api/openapi.json` in sync (it auto-regenerates from `OpenApiGeneratorTest` — run the test, do not hand-edit, and `git checkout` the incidental SBOM churn).
- **Javadoc is gate-enforced** on every new `public`/`protected` type and method — concrete, code-specific sentences; generic boilerplate fails Checkstyle.
- **Times in UTC** (`Instant`/`OffsetDateTime`); convert in the display layer only.
- **Tests via Gradle only** (never the IDE runner). Lint every touched file: `./gradlew :<module>:checkstyleMain :<module>:spotbugsMain` and fix **all** new Checkstyle/SpotBugs findings; run `./gradlew spotlessApply` before any push.
- **No native `confirm()`/`alert()`** in the frontend — KRT-styled modals/toasts only. Follow the DAS KARTELL design system; mobile→ultrawide responsive; 44px min touch target.
- **Logging** — `@Slf4j`; never log names, emails, or tokens.

For the desktop repo (`basetool-sc-extractor`), the analogous rules live in its `CLAUDE.md`: Kotlin official style; pure, unit-testable parser/transform functions; **`game-log/` stays private**; OFL-only fonts; ship the **MSI via `package-msi.ps1`** (WiX-3 workaround); JDK 25 pinned for compile **and** the bundled runtime; SC Fankit footer/notice stay intact.

---

## 9. Phases

> Dependency spine: **Phase 0 + the frozen contract (§5) unblock everything.** Phase 1 freezes the draft DTOs that Phase 2 consumes; Phase 2 needs Phase 1's endpoint; Phase 3 needs Phase 0's model/prompt + hardware tiers and emits the contract. Phases 1, 2 and 3 can otherwise proceed in **independent sessions** once §5 is frozen.

---

### Phase 0 — Spike: model, prompt, resolution pipeline, golden set, hardware tiers · #433

**Repo:** `basetool-sc-extractor` (currently `basetool-bp-extractor`). **Depends on:** nothing. **Unblocks:** 1, 2, 3.

**Objective.** De-risk the unknowns and produce the artifacts the build phases consume. No production code — a spike branch / scratch module + a written report.

**Inputs.** A handful of real **English SETUP** screenshots at several resolutions (1080p, 1440p, 4K, an ultrawide 5K). The owner's example set is at <https://nc.greluc.me/s/bbzFYL4PT4jkBKK>; treat them as **private** (do not commit real screenshots that contain a player handle; synthesize/redact for any committed fixture).

**Deliverables.**
1. **Model decision** — confirm `qwen3-vl` (size tag) reads SETUP rows accurately; identify a **low-VRAM fallback** (e.g. Gemma-class). Record accuracy per model/size on the golden set.
2. **Frozen prompt** — an externalized, versioned prompt that makes the VLM return **exactly** the §5 per-order/per-good JSON via Ollama's structured-output (`format` = JSON Schema). Store it as a resource, not inline, so it is updatable without a new MSI.
3. **Resolution-normalization POC** — demonstrate **Locate → Normalize → Read** working across 1080p–8K incl. ultrawide: locate the panel (VLM bbox on a downscaled frame, or a manual crop cached per resolution/aspect), normalize (crop native → up/downscale so row text lands in the model's sweet spot, target long edge ~1280–1600 px, row height ~32–48 px), read.
4. **Golden test set** — the screenshots + the **expected** `RefineryExtract` JSON for each, at multiple resolutions, as a regression baseline for Phase 3.
5. **Hardware tiers** — measured **VRAM/RAM/CPU** per model size and CPU-only speed (minutes/image); a **minimum vs. recommended** table; the "auto-select model size per detected VRAM, else warn + offer low-VRAM/CPU" policy; the "close SC before extracting" policy.
6. **Spike report** (`docs/refinery-extractor/PHASE0_FINDINGS.md` in the desktop repo) capturing all of the above + the column-semantics confirmation from §6.

**Design feed.** The measured hardware tiers feed the preflight UI in [`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) §5.1 — the min/recommended tier bar, the auto-selected-model chip, and the low-VRAM/CPU fallback copy. Record **GB-per-model-size** and **CPU minutes/image** as exact numbers so the preflight thresholds are not guesses.

**Definition of Done.** Report committed (when owner approves committing); the prompt + golden set + hardware table exist and are referenced by #436; `qwen3-vl` (or a justified alternative) is confirmed with numbers. No regression to the blueprint parser (179/`greluc` characterization stays green): `.\gradlew.bat test`.

**Out of scope (this phase).** Production GUI, the launcher, packaging, the backend, the frontend.

---

### Phase 1 — Backend import endpoint + master-data matching · #434

**Repo:** `basetool`. **Depends on:** frozen contract §5. **Unblocks:** Phase 2.

**Objective.** Accept a `RefineryExtract` JSON, match it to master data, and return a non-persisted `RefineryImportDraftDto`. **No persistence, no new tables.**

**Deliverables (files & signatures).**
- **Inbound DTOs** (`backend/.../model/dto/`), records with Jakarta validation mirroring §5: `RefineryExtractDto`, `RefineryExtractOrderDto`, `RefineryExtractGoodDto`, `RefineryExtractImageDto`. (`schemaVersion @NotNull`, `orders @NotEmpty @Valid`, per-good `@NotNull` where the contract requires it.)
- **Draft DTOs** (§7.5): `RefineryImportDraftDto`, `ImportIssueDto`, enums `ImportIssueCode`, `ImportIssueSeverity`.
- **Service** `RefineryImportService` (constructor-injected `MaterialRepository`, `RefiningMethodRepository`, `LocationRepository`/location lookup, `AuthHelperService`/`OwnerScopeService` as needed). Methods: `RefineryImportDraftDto buildDraft(RefineryExtractDto extract, UUID callerId)`; `Optional<Material> matchMaterial(String rawName)` (§7.3); `Optional<RefiningMethod> matchMethod(String rawName)`; `Optional<Location> matchRefineryLocation(String rawName)`. Keep matching **pure and unit-testable** (no `SecurityContextHolder` here).
- **Endpoint** on `RefineryOrderController` (or a new `RefineryImportController`):
  `POST /api/v1/refinery-orders/import-extract`, `consumes = application/json`, `@PreAuthorize("isAuthenticated()")`, `@RequestBody @Valid RefineryExtractDto`, returns `RefineryImportDraftDto`. SpringDoc `@Operation`/`@ApiResponses` (200 draft, 400 invalid/unsupported panel, 401). **Does not** create anything — the existing `POST /api/v1/refinery-orders` still does that, untouched.
- **Alias resource** (English) seeded from the Phase 0 golden set.
- **i18n** keys `refineryImport.issue.*` (one per `ImportIssueCode`) in all three `messages*.properties`.

**Design note (no UI in this phase).** The draft DTO must carry everything the review surface ([`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) §5.4) renders: per-row / per-field match state + confidence, unmatched / low-confidence flags, and the provenance fields (`tool`, `model`, `schemaVersion`, `panelType`, `generatedAt`) the export screen mirrors. `ImportIssueDto.confidence` + `code`/`severity` and the draft counts already cover this — keep them populated for every issue so the frontend can colour the confidence dots and count flagged fields without re-deriving anything.

**Algorithm.** Validate (§5 rules) → take `orders[0]` → for each good: skip-or-map per §5/§7.2, matching per §7.3, derive `outputMaterial` via `Material.refinedMaterial` → map order-level fields (§7.1) → assemble draft + issues. All times UTC.

**Tests (Gradle only).** Unit: `RefineryImportServiceTest` (exact match, case-insensitive, alias, fuzzy threshold accept/reject, `(ORE)` suffix strip, unmatched, refine-off skip, zero-qty skip, out-of-range quality, refinedMaterial derivation, multiple-orders truncation, unsupported panel). Controller `@WebMvcTest`/MockMvc: 200 happy path, 400 on `schemaVersion != 1`, 400 on `PROCESSING`, 401 unauthenticated. JSON binding test deserializing the §5 example verbatim. ArchUnit stays green.

**Definition of Done.** `./gradlew :backend:test` green; `./gradlew :backend:checkstyleMain :backend:spotbugsMain` clean for touched files; `openapi.json` regenerated via `OpenApiGeneratorTest`; CHANGELOG entry; Javadoc on every new type/method.

**Out of scope.** Persisting the order; the upload UI; any desktop code; PROCESSING.

---

### Phase 2 — Frontend upload + pre-filled review form · #435

**Repo:** `basetool` (frontend). **Depends on:** Phase 1 endpoint + draft DTOs.

**Objective.** Let the user upload the JSON, relay it to Phase 1, and render the **existing** refinery create form **pre-filled**, with every unmatched/low-confidence field visibly flagged for review before save.

**Deliverables.**
- **Frontend mirror DTOs** for `RefineryImportDraftDto` / `ImportIssueDto` (+ enums) under `frontend/.../model/dto/` — same fields (cross-cutting rule: mirror in the same change).
- **Upload proxy** `RefineryImportProxyController` modeled on `HangarImportProxyController`: `POST /refinery-orders/import` `consumes = multipart/form-data`, `@PreAuthorize("isAuthenticated()")`, `@RequestParam("file") MultipartFile`; read bytes, relay as **`application/json`** to `POST /api/v1/refinery-orders/import-extract` via the authenticated `WebClient`; translate `WebClientResponseException` → `ResponseStatusException`; return the draft as JSON to the browser. (Backend multipart cap is 2 MB — irrelevant here since the file is ~KB, but validate content length client-side.)
- **UI on the refinery create page** (`/refinery-orders`, `RefineryOrderPageController` + its Thymeleaf template + JS): an "Import from screenshot extract (JSON)" control (KRT-styled, **no** native dialogs). On upload: call the proxy, receive the draft, **populate the existing `RefineryOrderForm` fields and the goods rows** from `draft.order`, then render each `issues[]` entry as an inline KRT warning/badge on the relevant field (highlight unmatched material rows, missing location/method, low-confidence values). A summary banner: "X of Y rows matched, Z skipped — please review highlighted fields." Optimistic-locking note: this is a fresh create (no `@Version` yet), so no version-sync concern until first save.
- **i18n** keys `refineryImport.*` (button, help text, banner, per-issue messages) in all three `messages*.properties` (DE umlauts `\uXXXX`).
- Responsive (smartphone→ultrawide); 44 px touch targets.

**Design / UI acceptance (binding — [`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) §5.4).** The pre-filled review uses the §5.4 visual language: four **order-header cards** (Standort / Methode / Gesamtkosten / Dauer), each in a matched ✔ or needs-attention ⚠ state with a left status bar; a **goods table** with columns Material (raw, mono) · **Match** (green ✔ + master-data name, or a red "kein Treffer" chip + a `zuordnen` action) · Qualität · Input · Ausbeute (green `+`) · **Refine** toggle · **Konfidenz = percent + status dot** coloured ≥90 % success / 75–90 % warning / <75 % danger; unmatched / below-threshold rows get a coloured 3 px left border; a banner counts flagged fields; the "stays manual — please complete" chip row (owner, mission, otherExpenses, oreSales, startedAt) marks the empty/uncertain ones amber; `SETUP` + `layout NN%` badges sit in the header. Exactly **one** orange CTA = confirm. This is the **same** visual language the desktop Review screen uses (#436 §5.4) — keep them identical.

**Tests.** Frontend controller test for the proxy (WireMock/MockWebServer: backend 200 → draft passthrough; backend 400 → surfaced error). A `@SpringBootTest` MockMvc **full template render** test of the create page with a pre-filled draft (the project has been bitten by render-time 500s that pure controller tests miss). E2E (`e2e` label): upload a fixture JSON → form pre-filled → user completes an unmatched row → save succeeds. Align with `docs/e2e-test/UC-04-refinery-order-anlegen.md`.

**Definition of Done.** `./gradlew :frontend:test` green; frontend lint clean (Checkstyle/SpotBugs + the Node ESLint/Stylelint/HTMLHint linters); CHANGELOG; PR carries the `e2e` label (touches a frontend flow).

**Out of scope.** Direct desktop→backend upload (Phase 4); changing the create/save endpoint; PROCESSING fields (TO DO / DONE / total YIELD SCU / time remaining).

---

### Phase 3 — Desktop screenshot extraction (rebrand + launcher + Ollama + resource safety) · #436

**Repo:** `basetool-sc-extractor` (rebrand of `basetool-bp-extractor`). **Depends on:** Phase 0 (model, prompt, golden set, hardware tiers) + frozen contract §5.

**Objective.** Produce a `RefineryExtract.json` from SETUP screenshot(s), safely, on the user's PC.

**Deliverables.**
- **Rebrand & launcher.** Rename the app to `basetool-sc-extractor`; add a **central launcher GUI** that routes to the **Blueprint** workflow (existing) or the new **Refinery** workflow. **Widen the repo's `CLAUDE.md` scope** from "blueprints only" to "multi-workflow SC extractor". **Keep the blueprint parser characterization tests green** (179 / `greluc`) and its `game-log/` privacy guardrail intact through the restructure. Keep packaging via `package-msi.ps1` (WiX-3) and the JDK-25 `javaHome` pin.
- **Refinery pipeline** (pure, side-effect-free where possible, mirroring `BlueprintParser`'s testable style): `Locate → Normalize → Read → Stitch/dedupe`. Read step calls Ollama (`/api/generate` or `/api/chat`, `format` = the §5 JSON Schema, the Phase 0 prompt). Stitch/dedupe merges rows across scrolled screenshots into one order, de-duplicating repeated rows by `(rawMaterialName, inputQuantity)` and keeping the highest-confidence read. Emit the contract via a `@Serializable` model (bump its own `schemaVersion`).
- **Ollama integration (guided prerequisite).** On launch: check Ollama reachable (`GET /api/tags`); check the configured model present; if missing, offer `ollama pull <model>` with a **progress indicator**; clear, KRT-styled messaging if Ollama is absent (link to install docs). Endpoint + model name are **configurable**. **No bundling, no auto-install.**
- **Resource-safety guardrails (client side).** Preflight **hardware check** (detect GPU/VRAM + system RAM, or read Ollama's GPU detection); show **minimum vs. recommended** and **auto-select** a fitting model size, else warn and offer the **low-VRAM** model or **CPU mode** (works, slow); **detect a running `StarCitizen.exe`** and show a **soft, non-blocking** "close SC for safe extraction — continue anyway?" warning; **throttle** to one image through the model at a time; **never silently overload** — warn clearly when below minimum. All behind interfaces so they are unit-testable.
- **README** (German UI per repo convention) documenting Ollama install + `ollama pull`, the minimum/recommended hardware, and the "close SC first" guidance.

**Design / UI acceptance (binding — [`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md); build screen-for-screen to the prototype).**
- Custom KRT **title bar** (logo + "BASETOOL SC EXTRACTOR" + DE/EN toggle + min/max/close + orange hairline + resize grip).
- **Top-Tabs** launcher (Start · Blueprints · Refinery) + a per-workflow **step stepper**; Start = greeting + two workflow cards + fan-kit footer (Made-by-the-Community logo unaltered + verbatim CIG notice).
- Blueprint workflow restyled (Konfiguration · Extraktion · Zusammenfassung), one orange CTA per screen, neutral-grey labels, KRT tables.
- Refinery **Vorprüfung** implements every §5.1 state: Ollama reachable / model-present / model-missing (+ inline `ollama pull` progress) / unreachable (+ 2-step install hint); hardware GPU/VRAM/RAM + auto-selected-model chip + min/recommended tier bar + below-recommended fallback radio (low-VRAM model | CPU mode); SC-running soft warning + "trotzdem fortfahren" acknowledge checkbox; "one image at a time" throttle note.
- **Bilder laden** (1 folder = 1 order; thumbnail grid with resolution + crop tags), **Extraktion** (per-image Locate→Normalize→Read tracks, one active at a time, orange-accent console, model chip), **Review** (§5.4 — identical to #435), **Export** (written-path success + manual-upload instructions + provenance panel).
- Comfortable density, honeycomb texture (~0.32), DE/EN parity, **no native dialogs** (KRT modals/toasts only).

**Tests.** Pure-function unit tests (Kotlin, Gradle): stitch/dedupe across multi-image inputs, normalization math (resolution → crop/scale targets), contract serialization shape, the SC-process-detection and hardware-tier **decision logic** (mock the probes), Ollama-response → contract mapping (mock the HTTP call). Golden-set **regression** using Phase 0's recorded model outputs (do not require a live GPU in CI). Launch the GUI to confirm the slim runtime still boots (Skiko init).

**Definition of Done.** `.\gradlew.bat test` green incl. the preserved blueprint characterization; GUI launches; MSI builds via `package-msi.ps1`; README updated; the emitted JSON validates against §5 and is accepted by the Phase 1 endpoint end-to-end with a golden fixture.

**Out of scope.** Direct upload to the backend (Phase 4); PROCESSING parsing; log parsing (Phase 5).

---

### Phase 4 — Direct authenticated upload desktop → basetool · #437 · **DEFERRED (post-v1)**

**Deferred per owner decision (2026-06-05):** there is currently no way for the desktop tool to authenticate to and push to the backend, so v1 stays manual-upload-only (C3). Keep this issue open as a future option; do **not** implement it as part of the first delivery. When revisited it would add an OAuth2 device-code / PAT flow from the desktop app to a dedicated authenticated ingest endpoint, reusing the Phase 1 matching. Nothing in Phases 0–3 may assume this channel exists.

---

### Phase 5 — Log-derived location + timestamp hint · #438 · **DEFERRED (optional)**

The SC `Game.log` cannot provide the materials breakdown (verified: the only refine event `OnRefineryRequest` logs an empty `request[]`; the breakdown lives server-side at CIG). The log can at best contribute a **location + timestamp** hint to reduce manual entry. Optional, low priority, independent of Phases 0–3. Do not re-investigate the log-for-materials dead end.

---

## 10. Definition of Done — whole feature (v1)

- A user with a supported PC can: install Ollama + pull the model (guided), run `basetool-sc-extractor` → Refinery workflow, extract SETUP screenshot(s) to `RefineryExtract.json`, upload it on `/refinery-orders`, see the create form pre-filled with materials/quality/qty/yield/location/method/cost/duration, see unmatched/low-confidence items flagged, complete them, and save via the normal create path.
- The Hetzner VM never receives an image and never runs inference; disk/CPU impact is a small JSON parse + DB lookups.
- Every phase's tests pass via Gradle; all new Checkstyle/SpotBugs findings fixed; Javadoc/i18n/CHANGELOG/openapi gates satisfied; the blueprint tool's behavior and `game-log/` privacy are preserved.
- The rebuilt `basetool-sc-extractor` GUI and the frontend review surface match the binding Claude Design spec ([`docs/DESIGN_SC_EXTRACTOR.md`](DESIGN_SC_EXTRACTOR.md) + the prototype) screen-for-screen: Top-Tabs launcher, the §5.1 preflight states, the §5.4 review language (confidence = percent + dot), comfortable density, honeycomb texture, DE/EN parity, no native dialogs.

---

## 11. Glossary

- **SETUP screen** — the REFINEMENT CENTER tab where an order is configured (materials, quality, qty, yield, refine toggle, location, method, cost, time). v1 reads this only.
- **PROCESSING screen** — the in-progress/finished tab (TO DO / DONE / total YIELD SCU / time remaining). Deferred.
- **VLM** — vision-language model (here `qwen3-vl` via Ollama), run locally on the user's GPU.
- **RefineryExtract** — the frozen JSON contract (§5), the single desktop→frontend→backend hand-off.
- **Draft** — `RefineryImportDraftDto`: a non-persisted, best-effort pre-fill + issue list returned by the backend import endpoint.
- **Strict-staffel aggregate** — a basetool org-unit-scoped entity (Refinery Order is one); scope is enforced in the service layer via `OwnerScopeService`.
