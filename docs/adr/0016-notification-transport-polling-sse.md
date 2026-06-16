# ADR-0016 — Notification delivery: in-app polling baseline, SSE push as enhancement

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** @greluc
- **Related:** spec REQ-NOTIF-006, REQ-NOTIF-010 ([`notifications.md`](../specs/notifications.md)) ·
  ADR-0014 · epic [#622](https://github.com/krt-iri/basetool/issues/622)

## Context

The unread indicator must be visible top-right on every page and reflect new notifications
without the user reloading. Two delivery mechanisms were on the table: client polling and a
real-time server push (SSE or WebSocket). The backend is a Spring MVC servlet app with no
existing backend→frontend push bus (the only WebSocket is the frontend-local mission-presence
channel, browser↔frontend, with no backend involvement). The deployment is a single VM.

## Decision

We will ship **in-app polling as the baseline and the always-available mechanism**: the bell
badge is rendered server-side on every page (fail-soft to 0) and refreshed by a client-side
poll and after every mutation, always sourced from the server count so it cannot go stale.

Real-time **push will use SSE** (one-way server→client, simpler than WebSocket and a natural fit
for a notification stream), as an **enhancement layered on top of polling** with polling as the
fallback. v1 push is a single-backend-instance in-memory emitter registry keyed by `sub`;
multi-instance fan-out via Redis pub/sub is a noted follow-up. SSE delivery is tracked as its own
phase ([#630](https://github.com/krt-iri/basetool/issues/630)) and is not required for the inbox
to function.

OS / browser push notifications are out of scope — the indicator is in-app only.

## Consequences

- **Easier:** the inbox works fully on polling alone, with no new backend infrastructure; SSE
  can be added without changing the data model or the inbox API.
- **Harder / accepted:** polling has up-to-poll-interval latency until SSE lands; an in-memory
  emitter registry is single-instance only (matches the single-VM deployment; multi-instance is
  deferred).

## Alternatives considered

- **WebSocket for push** — rejected: bidirectional and heavier than needed for a one-way
  server→client stream; SSE carries less protocol surface.
- **Polling only, no push ever** — rejected as the long-term answer: acceptable as the baseline
  but real-time delivery is a stated goal (REQ-NOTIF-010).
- **Reusing the mission-presence WebSocket** — rejected: it is frontend-local (no backend
  bus); a backend→frontend channel is genuinely new.

