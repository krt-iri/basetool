# ADR-0008 — `RefineryExtract` JSON as the frozen cross-repo contract

- **Status:** Accepted
- **Date:** 2026-06-10
- **Deciders:** Lucas Greuloch (@greluc)
- **Related:** epic [#439](https://github.com/krt-profit/basetool/issues/439), [`docs/REFINERY_SCREENSHOT_IMPORT_PLAN.md`](../REFINERY_SCREENSHOT_IMPORT_PLAN.md) §5, [`docs/specs/refinery-screenshot-import.md`](../specs/refinery-screenshot-import.md) REQ-REFINERY-001, [ADR-0007](0007-client-side-vlm-screenshot-extraction.md)

## Context

ADR-0007 splits the feature across two repos: the desktop extractor produces the
screenshot reads, the basetool backend consumes them. Phases 1 (backend, #434), 2
(frontend relay, #435) and 3 (desktop, #436) are built in independent sessions and must
not block each other. That only works if the data shape between them is fixed **before**
the phases run and cannot drift silently afterwards.

## Decision

The single integration point is the `RefineryExtract` JSON document, frozen as contract
**v1** in the master plan §5 and mirrored 1:1 by the backend's `RefineryExtractDto`
record family. Key shape decisions:

- An explicit integer `schemaVersion` (currently `1`); **any** breaking shape change
  bumps it, and the backend rejects unknown versions loudly (400 with a localized
  message) instead of guessing.
- Verbatim screen reads (`rawMaterialName`, `rawLocationName`, `rawMethodName`, header
  totals) — the producer never resolves master data; all matching is backend-side, so
  catalogue changes never require an extractor release.
- Provenance fields (`tool`, `toolVersion`, `model`, `generatedAt`, `clientLanguage`,
  per-image `cropMode`) for display and debugging only.
- Read-quality signals as data, not behaviour: order-level `quoted` + `layoutConfidence`,
  per-row `rowIndex`, nullable `outputQuantity` (un-quoted state) and **derived**
  per-row `confidence` (deterministic validation + header checksum, the frozen Phase 0
  policy — the originally planned two-pass agreement was rejected by the golden-set
  data; never the model's verbalized self-estimate).
- A binding JSON example lives in the plan §5 and is deserialized verbatim by
  `RefineryExtractDtoJsonTest`, so contract drift fails the build.

## Consequences

- Phases 1/2/3 proceed in parallel; the desktop tool tests against the same example
  documents the backend's binding test uses.
- The backend carries the full matching burden (canonicalization, aliases, fuzzy) —
  accepted, because master data lives there anyway (REQ-REFINERY-004).
- Contract evolution is explicit: new optional fields may land within v1; anything
  breaking requires `schemaVersion: 2`, a backend that accepts both during a grace
  window, and an updated plan §5 + this ADR's successor.
- The frontend never interprets the extract; it relays the bytes and renders the
  backend's draft + issues (Phase 2).

## Alternatives considered

- **Multipart upload of raw screenshots to the backend:** rejected by ADR-0007
  (privacy, GPU).
- **Producer-side master-data resolution (ship the extractor a catalogue dump):**
  rejected — stale catalogues on user machines would produce silently wrong IDs; raw
  names + server-side matching degrade visibly instead (issues + suggestions).
- **A binary/protobuf contract:** rejected — JSON is human-inspectable (the user is
  explicitly meant to be able to read what gets uploaded), trivially versioned, and the
  payload is tiny.
- **No version field, duck-typed parsing:** rejected — the project has been bitten by
  silent shape drift between modules before; loud rejection is a deliberate property.

