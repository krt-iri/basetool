> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-07.
> **Owner area:** INV · **Related ADRs:** none · **Plan:** `SC_WIKI_SYNC_PLAN.md` (historical)

# Blueprint import — product name matching

## Context & goal

The personal-inventory blueprint feature ([#327](https://github.com/greluc/basetool/issues/327))
lets a user own crafting blueprints and import them from the game. The in-game
`"Received Blueprint: <name>"` notification — captured by the SCMDB log-watcher / Basetool
Blueprint Extractor — is matched, by name, against the blueprint **product master**.
`BlueprintProductService` builds that master from `blueprint.output_name`, which
`ScWikiBlueprintSyncService` syncs verbatim from the SC Wiki `/api/blueprints` feed. So the
import only finds a product if the in-game name it sends equals a stored `output_name` — after
both are normalized. This spec governs that name-matching contract and the correction layer that
keeps it working when CIG mislabels a name at the source.

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

## Out of scope

- The resolved `output_item` / `game_item` name (the recipe-detail view may still show a wrong
  item name) — a separate concern resolved by `external_uuid`, not by this name-correction layer.
- The fuzzy-suggestion ranking of the import matcher itself (it consumes the corrected master).
- The broader SC Wiki / UEX / P4K catalog-sync mechanics, documented in the historical
  `SC_WIKI_SYNC_PLAN.md` and the sync services' Javadoc.

## Open questions

None.
