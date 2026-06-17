> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-06-06.
> **Owner area:** OBS · **Related:** [`security-and-access.md`](security-and-access.md), [`org-unit-tenancy.md`](org-unit-tenancy.md)

# Observability & logging

## Context & goal

Every request is traceable end-to-end across both modules via shared MDC fields, with
machine-parseable JSON logs in prod — and never any PII in the logs.

## Requirements

### REQ-OBS-001 — Access log + MDC enrichment

Both modules emit one access-log line per request and enrich every log line with MDC fields
`correlationId`, `userId`, and `orgUnitId` (the last per
[`org-unit-tenancy.md`](org-unit-tenancy.md) REQ-ORG-007). Logback patterns must include
`%X{orgUnitId}` to keep audit trails intact.

### REQ-OBS-002 — Correlation-id propagation

`correlationId` comes from the inbound `X-Correlation-Id` header (configurable via
`APP_LOGGING_CORRELATION_ID_HEADER`) or a generated UUID, and is echoed in the response
header. The frontend's `WebClientLoggingFilter` propagates the same id to outbound backend
calls so both modules share one id per user interaction. `userId` is the JWT `sub`, or
`anonymous`.

### REQ-OBS-003 — Prod JSON appender

In `prod`, a `LogstashEncoder` JSON appender writes `logs/{backend,frontend}.json`; errors
split into `*-error.log` for fast triage. Configurable via `APP_LOGGING_*` env vars.

### REQ-OBS-004 — Never log PII

**Never log names, emails, or tokens.** This is unconditional and applies to every log
level and both modules. "Names" includes the Keycloak `preferred_username` / callsign handle:
the `PiiMasker` only scrubs JWTs, e-mail-shaped strings and token keywords, so a bare handle
would reach the appenders verbatim — log the user's `sub` UUID instead (the row id is in the
same UUID space and is not PII).
