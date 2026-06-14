> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-12.
> **Owner area:** INV · **Related ADRs:** [ADR-0008](../adr/0008-refinery-extract-json-contract.md)
> (its additive-v1 evolution rule is mirrored by REQ-INV-014) · **Plan:** `SC_WIKI_SYNC_PLAN.md` (historical)

# Blueprint import — product name matching

## Context & goal

The personal-inventory blueprint feature ([#327](https://github.com/greluc/basetool/issues/327))
lets a user own crafting blueprints and import them from the game. The in-game
`"Received Blueprint: <name>"` notification — captured by the SCMDB log-watcher / Basetool
Blueprint Extractor — is matched, by name, against the blueprint **product master**.
`BlueprintProductService` builds that master from `blueprint.output_name`, which
`ScWikiBlueprintSyncService` syncs verbatim from the SC Wiki `/api/blueprints` feed. So the
import only finds a product if the in-game name it sends equals a stored `output_name` — after
both are normalized. This spec governs that name-matching contract, the correction layer that
keeps it working when CIG mislabels a name at the source, and the tolerance rules for the
uploaded export file's envelope.

## Requirements

### REQ-INV-006 — Import matches on the normalized `output_name`

The blueprint import and product search match the in-game/log product name against the master
list of `blueprint.output_name`s, comparing both sides through the single
`BlueprintNameNormalizer` (trim, collapse internal whitespace, fold Unicode quote/apostrophe
glyphs to ASCII, lowercase). Normalization is conservative — it must not strip punctuation
wholesale, which could collide genuinely distinct products. The master product list is built
from `output_name` (not the resolved `output_item`/`game_item` name), so a blueprint whose
`output_name` is wrong cannot be matched under its true name.

**Acceptance**

- [ ] Search and import normalize names through `BlueprintNameNormalizer` before comparing.
- [ ] The product master is grouped by the normalized `output_name`.
- [ ] Casing / surrounding & internal whitespace / quote-glyph differences do not defeat a match.

**Enforced by:** `BlueprintNameNormalizerTest`, `BlueprintProductServiceTest` · **Code:**
`BlueprintNameNormalizer`, `BlueprintProductService`, `ScWikiBlueprintSyncService` · **Issues:**
[#327](https://github.com/greluc/basetool/issues/327)

### REQ-INV-007 — Curated, guarded, self-healing correction of CIG-mislabeled `output_name`s

Some blueprints carry a wrong English `output_name` because CIG's game data (`Data.p4k`) has a
mislabeled localization string; the SC Wiki mirrors it faithfully and the basetool copies it, so
the correct in-game name exists under no entry and the import can only fuzzy-suggest a wrong
piece. The sync applies a curated correction layer — a small, immutable, developer-maintained
code map (`BlueprintOutputNameOverrides`) keyed on the structurally-correct `scwiki_key`, each
entry `(expectedWrongName, correctedName)` — immediately before persisting `output_name`. The
correction is:

- **Guarded:** it fires only when the incoming `output_name`, normalized via
  `BlueprintNameNormalizer`, equals the equally-normalized `expectedWrongName` for that key.
  Otherwise the upstream value passes through unchanged.
- **Self-healing:** once CIG (or the Wiki) fixes or changes the string the guard stops matching
  and upstream wins automatically — the override never overwrites a name it does not recognise,
  so it cannot silently go stale.
- **Observable when obsolete:** when a registered override's `scwiki_key` is present in the feed
  this run but its `expectedWrongName` no longer matches (so the override did not fire), the sync
  emits a `SyncEventType.BLUEPRINT_NAME_OVERRIDE_OBSOLETE` event so an operator removes the entry.

The correction is applied identically on the P4K seed path (`P4kImportService.maybeSeedBlueprint`,
which also writes `output_name`) for consistency. It touches **only** `output_name` — never the
resolved `output_item`/`game_item` name. The seeded set are the two confirmed CIG bugs:

|                  `scwiki_key`                   | wrong `output_name` | corrected (in-game) name |
|-------------------------------------------------|---------------------|--------------------------|
| `BP_CRAFT_qrt_specialist_heavy_arms_01_01_13`   | `Antium Helmet Jet` | `Antium Arms Maroon`     |
| `BP_CRAFT_qrt_specialist_heavy_helmet_01_01_12` | `Antium Core Jet`   | `Antium Helmet Jet`      |

A DB-backed, admin-managed table is an explicit non-goal: this is a small, developer-curated set
of known upstream bugs that need the correct name in code review, not a runtime-editable list.

**Acceptance**

- [ ] The correction fires (stores `correctedName`) when the registered wrong name is present.
- [ ] It passes the upstream value through when CIG fixed/changed the name (different/correct
  incoming name) and for any unregistered `scwiki_key`.
- [ ] The guard is normalization-insensitive (differing case/spacing still corrects).
- [ ] A `BLUEPRINT_NAME_OVERRIDE_OBSOLETE` event is emitted when a registered key is seen but its
  wrong name is gone.
- [ ] The two seed entries above are present.

**Enforced by:** `BlueprintOutputNameOverridesTest`, `ScWikiBlueprintSyncServiceTest`,
`P4kImportServiceTest` · **Code:** `BlueprintOutputNameOverrides`,
`ScWikiBlueprintSyncService.upsertBlueprintWithinTransaction`,
`P4kImportService.maybeSeedBlueprint` · **Issues:**
[#327](https://github.com/greluc/basetool/issues/327)

### REQ-INV-014 — Tolerant export-envelope parsing (additive v1 evolution)

The import accepts three upload shapes: a bare JSON array of entries, the SCMDB log-watcher
document, and the Basetool Blueprint Extractor `BlueprintExport` document (`schemaVersion` 1).
In the document forms only the top-level `blueprints` array is consumed; every other envelope
field is tolerated and ignored (`@JsonIgnoreProperties(ignoreUnknown = true)` on
`BlueprintExportFileDto`). The extractor evolves its export contract additively within schema
version 1 — the same rule ADR-0008 fixes for the refinery extract (precedent: `capturedAt` on
`sourceImages`, 2026-06-11) — so new nullable envelope fields appear without a version bump and
must never break the import.

The first such field is `additionalSourceFolders` (`List<String>`, nullable, default `null`):
the extra game-channel folders the extractor scanned beside its primary `sourceFolder`
(currently the `HOTFIX` sibling of `LIVE`). Because the extractor encodes defaults, the key is
always present in its exports — as JSON `null` when only the primary folder was scanned. The
field is mirrored on `BlueprintExportFileDto` for contract explicitness but is provenance only;
the import does not consume it.

**Acceptance**

- [ ] Bare-array, SCMDB-document, and Extractor-document uploads all parse.
- [ ] An Extractor export with `additionalSourceFolders` populated, explicitly `null`, or absent
  (older extractor version) imports identically.
- [ ] Unknown envelope fields never fail the parse.

**Enforced by:** `BlueprintImportServiceTest` (`preview_acceptsBareArrayForm`,
`preview_acceptsFullScmdbWatcherDocumentShape`, `preview_acceptsBpExtractorReceivedAtFormat`,
`preview_acceptsBpExtractorWithAdditionalSourceFolders`,
`preview_acceptsBpExtractorWithNullAdditionalSourceFolders`) · **Code:**
`BlueprintExportFileDto`, `BlueprintImportService#parse` · **Issues:**
[#327](https://github.com/greluc/basetool/issues/327)

### REQ-INV-015 — Variant family key (cosmetic-variant grouping)

Two features that count blueprint ownership across members — the item-order coverage view
(`REQ-ORDERS-015`) and the org-unit availability overview (`REQ-INV-012`) — group a base item and
its **cosmetic variants** into one craftable *family*, so owning any family member counts. The
grouping is **name-based** (the catalog's `game_item.is_base_variant` / `class_name` are nullable,
ship dark by default, and unreachable from a `PersonalBlueprint`, which is keyed by `product_key`
string only), via a single `BlueprintVariantFamilyResolver.familyKey(name)` both sides call:

- A cosmetic variant is the base name with a **quoted nickname** spliced in (`Fresnel Energy LMG` →
  `Fresnel "Molten" Energy LMG`; `Novian Crossbow` → `Novian "Wildshot" Crossbow`). The family key is
  the `BlueprintNameNormalizer`-normalized name with every ASCII double-quoted span removed and
  whitespace re-collapsed — so all family members reduce to the same key. The rule is **conservative**:
  it keeps the full unquoted residue (incl. the weapon-type word like `Rifle`/`SMG`/`Crossbow`), which
  prevents cross-family collisions (`Sawtooth "Sirocco" Combat Knife` ≠ a `Karna` rifle) and leaves
  genuinely distinct unquoted products distinct (ship sub-models `Aurora MR` vs `Aurora LN`).
- **Magazines are never variants.** A name detected as an ammo container — a `(NNN cap)` capacity
  parenthetical (a digit run + the whole word `cap`), or the standalone noun `magazine` / `battery` /
  `ammo box` — gets an **atomic** family key that can only equal an identical magazine. It folds into
  no weapon family, and two capacities of the "same" magazine stay distinct.
- A small, curated, guarded/self-healing **alias map** (`BlueprintVariantAliasOverrides`, mirroring
  `BlueprintOutputNameOverrides`, `REQ-INV-007`) canonicalizes the residual same-line cases the
  structural rule cannot merge — base-name spelling drift (`Pulse "Blacklist" Pistol` → `pulse pistol`
  folded onto `pulse laser pistol`) and confirmed unquoted sub-models (`Salvo Esteban Frag Pistol`,
  `Model II Arclight`). Each entry is a deliberate game-domain judgement; a non-matching key is a no-op.

**Acceptance**

- [ ] `familyKey(base) == familyKey(variant)` for every quoted-nickname cosmetic variant, in both
  directions, surviving the normalizer (case, spacing, curly quotes, apostrophes inside the nickname).
- [ ] A magazine's family key is atomic and never equals its weapon's; the `cap` test never matches a
  substring (`capacitor`/`capstone`) nor a non-capacity parenthetical (`(Modified)`).
- [ ] Genuinely distinct unquoted products (ship sub-models, cross-family names) do not merge.
- [ ] A registered alias canonicalizes; an unregistered key passes through unchanged.

**Enforced by:** `BlueprintVariantFamilyResolverTest`, `BlueprintVariantAliasOverridesTest` ·
**Code:** `BlueprintVariantFamilyResolver`, `BlueprintVariantAliasOverrides` · **Issues:** —

## Out of scope

- The resolved `output_item` / `game_item` name (the recipe-detail view may still show a wrong
  item name) — a separate concern resolved by `external_uuid`, not by this name-correction layer.
- The fuzzy-suggestion ranking of the import matcher itself (it consumes the corrected master).
- The broader SC Wiki / UEX / P4K catalog-sync mechanics, documented in the historical
  `SC_WIKI_SYNC_PLAN.md` and the sync services' Javadoc.

## Open questions

None.
