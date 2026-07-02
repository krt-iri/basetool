> **Doc type:** Living spec — kept in sync with `main`. Last reviewed: 2026-07-02.
> **Owner area:** OBS · **Related:** [`security-and-access.md`](security-and-access.md), [`org-unit-tenancy.md`](org-unit-tenancy.md), [ADR-0061](../adr/0061-monitoring-stack-prometheus-grafana.md), monitoring epic [#936](https://github.com/krt-profit/basetool/issues/936)

# Observability & logging

## Context & goal

Every request is traceable end-to-end across all three modules (backend, frontend, ingest)
via shared MDC fields, with machine-parseable JSON logs in prod — and never any PII in the
logs. The monitoring stack (Prometheus/Grafana/Loki/Tempo/Alloy, epic
[#936](https://github.com/krt-profit/basetool/issues/936), ADR-0061) builds on these
guarantees; REQ-OBS-005 onward record its binding rules.

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

Errors raised **before** `CorrelationIdFilter` runs — the rate-limit 429, the pending-approval 403,
and the Spring Security filter-level 401/403 — mint their own `correlationId`, put it in the MDC (so
the problem body and the WARN log line share it), and echo it as the `X-Correlation-Id` response
header themselves, because that filter never runs to echo it on a short-circuited request. Every
error response therefore carries the header, not just the ones that reach the servlet. See
[`api-conventions.md`](api-conventions.md) REQ-API-004 for the full producer list.

### REQ-OBS-003 — Prod JSON appender

In `prod`, a PII-masking `LogstashEncoder` JSON appender writes `logs/{backend,frontend}.json`;
errors split into `*-error.log` for fast triage. Configurable via `APP_LOGGING_*` env vars.
The ingest gateway emits its prod JSON to stdout through the same `PiiMaskingLogstashEncoder`
pattern (no file sink — its stream is collected via the Docker log API).

### REQ-OBS-004 — Never log PII

**Never log names, emails, or tokens.** This is unconditional and applies to every log
level and all three modules. "Names" includes the Keycloak `preferred_username` / callsign handle:
the `PiiMasker` only scrubs JWTs, e-mail-shaped strings and token keywords, so a bare handle
would reach the appenders verbatim — log the user's `sub` UUID instead (the row id is in the
same UUID space and is not PII).

### REQ-OBS-005 — Prometheus metrics endpoint, fail-closed

All three modules expose Micrometer metrics at `GET /actuator/prometheus` for the monitoring
scrape (epic #936, ADR-0061). The endpoint is **never public**:

- Each module guards exactly this path with a **dedicated `SecurityFilterChain`**
  (`MonitoringScrapeSecurityConfig`, ordered before the main chain) using HTTP basic auth
  against a single in-memory scrape identity fed by the `MONITORING_SCRAPE_USER` /
  `MONITORING_SCRAPE_PASSWORD` environment variables (BCrypt-hashed at startup).
- **Fail-closed:** with either variable unset/blank the chain is built with `denyAll()` —
  there is no unauthenticated fallback. Dev/test/e2e and a prod host without the monitoring
  stack therefore expose nothing.
- Only the scrape identity counts: a valid Keycloak JWT (backend/ingest) or a logged-in
  browser session (frontend) must **not** grant access to the metrics payload.
- The scrape chain is stateless (no session, no CSRF token, no request cache) so a 30-second
  scrape interval creates no session state; a scrape response never carries `Set-Cookie`.
- `/actuator/health` remains public for the Docker `HEALTHCHECK` — unchanged by this
  requirement. The frontend `BotProtectionFilter` whitelists `/actuator/health` (incl. its
  liveness/readiness sub-paths, case-insensitively — pre-existing behaviour) and
  `/actuator/prometheus` as an **exact, case-sensitive match only**, mirroring the scrape
  chain's `securityMatcher`; prometheus sub-paths/case variants and every other
  `/actuator/**` path stay blocked with 404 before the security chains run.
- **Prod precondition for setting the credentials:** the NPM `/actuator` deny rules on both
  public hosts (`profit-base.online`, `ingest.profit-base.online`) are applied **before**
  `MONITORING_SCRAPE_*` is deployed (Phase-2 runbook), so the credentialed endpoint is only
  reachable from the internal scrape network. This is also the compensating control for the
  per-request BCrypt cost of basic auth — without the edge deny, an internet client could
  drive unthrottled credential guesses / CPU load against the endpoint (residual-risk record
  in ADR-0061).
- Every meter carries the common tag `application=basetool-{backend,frontend,ingest}` so
  dashboards can select the module.

### REQ-OBS-006 — No PII and no unbounded labels in metrics

Metric names, label keys, label values and measured values must never contain
user-identifying data (usernames, e-mails, `sub` UUIDs, IPs, tokens) or free text. Labels
must come from **bounded, enumerable sets** (status enums, task names, HTTP status classes,
cache names); per-user, per-entity-id or otherwise unbounded label values are forbidden —
both as a privacy rule (metrics have 180-day retention) and as a cardinality guard for the
Prometheus TSDB. This applies to every meter exposed on `/actuator/prometheus`, including
the future `basetool_*` business metrics (epic #936 Phase 1c).

### REQ-OBS-007 — Log ingestion into the monitoring plane (per-stream rules)

When log streams are shipped to Loki (epic #936 Phase 2), each stream obeys its own recorded
rule — no blanket "everything is masked" claim:

- **backend / frontend / ingest JSON** — PII-masked **at the source** (REQ-OBS-003/-004);
  the shipper adds no further masking.
- **Keycloak file log** — masked **in the shipper** (Alloy stages scrub `username=` /
  `ipAddress=` before ingestion).
- **NPM access logs, NPM admin-UI stdout, and SSH/host-auth logs** — ingested **including
  client IPs and usernames** at a **31-day retention**. This is a deliberate,
  owner-approved data-protection decision (2026-07-02) for security monitoring and abuse
  detection; it is conditioned on the privacy-policy extension (`privacy.html` + DE/EN
  bundles) that ships with the Phase-2 PR, and Loki is deliberately excluded from backups so
  the GFS retention cannot silently extend those 31 days (ADR-0061).
- **PostgreSQL container logs** — ingested with `log_error_verbosity=terse` so `DETAIL`
  lines cannot leak row data.
- Loki labels stay low-cardinality (`app`, `level`, bounded `host`); log lines are never
  turned into per-user labels.

### REQ-OBS-008 — Monitoring plane: admin-only access, isolated cleartext carve-out

- **Admin-only UIs:** Grafana is the **only** monitoring UI, published via NPM and
  authenticated through Keycloak OIDC restricted to the realm role `Admin`
  (`ROLES_AND_PERMISSIONS.md`). No other monitoring component (Prometheus, Alertmanager,
  Loki, Tempo, exporters) exposes a host port or a public route; their APIs are reachable
  only inside the isolated monitoring Docker networks.
- **Transport:** all app/Keycloak/NPM edges stay HTTPS — Prometheus scrapes the three
  modules over HTTPS validating the pinned public certificate (no `insecure_skip_verify`).
  **Inside** the isolated monitoring networks (Prometheus→exporters, Grafana→datasources,
  Alloy→Loki/Tempo, →`keycloak:9000`) traffic is deliberately plain HTTP. This carve-out is
  owner-approved (2026-07-02, epic #936) and amends the HTTPS-only posture; the REQ-SEC-014
  wording in [`security-and-access.md`](security-and-access.md) is amended in the Phase-2 PR
  that actually creates those networks. Rationale and residual risk live in ADR-0061.
- The private key of the shared `keystore.p12` never leaves the four existing services;
  Grafana gets its own self-signed certificate.

