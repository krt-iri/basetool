# Architecture Decision Records (ADRs)

An ADR captures **one architecturally significant decision** — the context that forced a
choice, the decision itself, and the consequences we accept by making it. ADRs answer
*"why is it built this way?"* long after the people who decided have moved on.

Specs (in [`docs/specs/`](../specs/INDEX.md)) say *what must hold*. ADRs say *why we chose
this approach over the alternatives*. The two reference each other.

## When to write one

Write an ADR when a decision is **hard to reverse** or **non-obvious**: a framework or
protocol choice, a security posture, a data-model trade-off, a cross-cutting pattern (e.g.
the `…WithinTransaction` concurrency rule). Routine, easily-reversed choices do not need
one.

## Format

We use a lightweight [MADR](https://adr.github.io/madr/)-style format — copy
[`0000-template.md`](0000-template.md).

- **Numbering:** zero-padded, sequential, never reused — `0001`, `0002`, …
- **Filename:** `NNNN-kebab-case-title.md`.
- **Immutable once Accepted.** Do not rewrite an accepted ADR to reflect a *new* decision.
  Write a new ADR that supersedes it, and set the old one's status to
  `Superseded by ADR-NNNN`. The record of *why it changed* is the whole point.

## Status lifecycle

|          Status          |                                  Meaning                                  |
|--------------------------|---------------------------------------------------------------------------|
| `Proposed`               | Under discussion; not yet binding.                                        |
| `Accepted`               | The decision is in force (implementation may still be pending — note it). |
| `Superseded by ADR-NNNN` | Replaced by a later decision.                                             |
| `Deprecated`             | No longer applies; not directly replaced.                                 |

## Index

|                         ADR                         |                                         Title                                         |              Status               |
|-----------------------------------------------------|---------------------------------------------------------------------------------------|-----------------------------------|
| [0001](0001-frontend-confidential-oauth2-client.md) | Frontend as a confidential OAuth2 client (PKCE + secret)                              | Accepted — implementation pending |
| [0002](0002-whole-number-amounts.md)                | Whole-number amounts: value-based validation, reject-not-round, display-only rounding | Accepted                          |
| [0003](0003-inventory-append-only-group-on-read.md) | Inventory: append-only entries with group-on-read display                             | Accepted                          |
| [0004](0004-ownerless-leadership-missions.md)       | Ownerless leadership ("Bereichsleitung") missions                                     | Accepted                          |
| [0005](0005-ownerless-leadership-operations.md)     | Ownerless leadership ("Bereichsleitung") operations                                   | Accepted                          |
| [0006](0006-operation-participant-visibility.md)    | Operation visibility for mission participants                                         | Accepted                          |

