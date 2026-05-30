# E2E-Test Use Cases

Dieses Verzeichnis dokumentiert die End-to-end-Testszenarien des Profit Basetool als Use Cases — je ein Dokument pro funktionalem Flow. Jeder Use Case beschreibt Akteur, Vorbedingungen, Ablauf und erwartetes Ergebnis und verlinkt die implementierende Playwright-Testklasse.

Für Architektur, Phasenplan und Designentscheidungen der E2E-Suite siehe [`../E2E_TESTING_PLAN.md`](../E2E_TESTING_PLAN.md).

## Übersicht

| ID | Use Case | Tag | Testklasse |
|----|----------|-----|------------|
| [UC-01](UC-01-login.md) | Login via Keycloak | `e2e` | `LoginSmokeE2eTest` |
| [UC-02](UC-02-mission-anlegen.md) | Einsatz anlegen | `e2e` | `MissionCreateE2eTest` |
| [UC-03](UC-03-job-order-anlegen.md) | Job Order anlegen | `e2e` | `JobOrderCreateE2eTest` |
| [UC-04](UC-04-refinery-order-anlegen.md) | Refinery Order anlegen | `e2e` | `RefineryOrderCreateE2eTest` |
| [UC-05](UC-05-hangar-schiff-hinzufuegen.md) | Schiff zum Hangar hinzufügen | `e2e` | `HangarAddShipE2eTest` |
| [UC-06](UC-06-job-order-handover.md) | Job-Order-Handover protokollieren | `e2e` | `JobOrderHandoverE2eTest` |
| [UC-07](UC-07-kernseiten-smoke.md) | Kernseiten-Smoke (nicht-destruktiv) | `smoke` | `CorePagesSmokeE2eTest` |

UC-01 bis UC-07 sind als Playwright-Tests implementiert (Happy Path als Admin/IRIDIUM-Mitglied).

### Rollen & staffel-/SK-übergreifend

Die folgenden Dokumente erweitern die Grund-Flows um **Rollen** (Offizier, einfaches Mitglied) und **Mehr-Staffel-/SK-Szenarien** — inkl. der Fälle, in denen eine Staffel etwas anlegt und eine andere damit weiterarbeitet. Sie sind **spezifiziert; die zugehörigen Tests folgen** (Reihenfolge „erst Doku, dann Tests").

| Dokument | Thema |
|----------|-------|
| [Rollen & Scope](rollen-und-scope.md) | Rollen × Flow-Matrix, Mandanten-Scope-Modell, Admin-Pin, SK-Grundlagen (Referenz) |
| [UC-08](UC-08-job-order-staffel-uebergreifend.md) | Job Order: Staffel A bestellt, Staffel B liefert (B's Inventar verknüpft) |
| [UC-09](UC-09-handover-staffel-uebergreifend.md) | Handover staffel-übergreifend (Material von B, Empfänger ggf. dritte Staffel) |
| [UC-10](UC-10-mission-staffel-uebergreifend.md) | Öffentlicher Einsatz mit Teilnehmern aus anderer Staffel |
| [UC-11](UC-11-sk-spezialkommando.md) | Spezialkommando (SK) als OrgUnit (Lifecycle, Mitglieder, aktuelle Grenzen) |

> **Hinweis zur Abdeckung:** Einsätze/Operationen und Refinery Orders sind **strict-staffel** (nicht staffel-übergreifend). Die staffel-übergreifende Zusammenarbeit läuft über öffentliche Einsätze (UC-10) und den Job-Order-Workspace inkl. Handover (UC-08/UC-09). Details in [Rollen & Scope](rollen-und-scope.md).

## Gemeinsamer Rahmen

**Akteur.** Sofern nicht anders genannt, ist der Akteur der synthetische Test-User `test-admin` (Keycloak) — nach dem Login eine authentifizierte Session mit Mitgliedschaft in der IRIDIUM-Staffel. Der Test-User hat die ADMIN-Rolle; staffel-scoped Aktionen (Einsatz, Ship, Refinery Order) verlangen eine OrgUnit-Mitgliedschaft, die der Seeder herstellt.

**Ziel-Modi.** Die Suite ist ziel-agnostisch:

- *Ephemerer Stack* (Default): `E2eStackExtension` fährt den vollen Stack (Postgres ×2 + Keycloak + Redis + Backend + Frontend) per `docker compose` hoch, seedet die Vorbedingungen und reißt ihn danach ab (`down --volumes`).
- *Staging*: Mit gesetztem `E2E_BASE_URL` laufen die Tests gegen ein externes Deployment; Docker wird nicht angefasst.

**Daten-Setup.** Die Vorbedingungen werden im `@BeforeAll` hergestellt (nur im ephemeren Modus):

- `BackendSeeder` — über die Backend-REST-API mit Bearer-Token (Keycloak-Password-Grant): IRIDIUM-Mitgliedschaft, Materialien, Locations, Job Orders, verknüpftes Inventar.
- UEX-Katalog-Snapshot (`uex-catalog-seed.sql`) — per JDBC: refinery-fähige Location, ShipType und Refining Method. Diese sind normalerweise UEX-synced und über die Admin-API auf einer frischen DB nicht anlegbar.

**Browser.** Chromium (Default), Firefox und WebKit — wählbar via `-Pe2e.browser`, in der CI als Matrix.

**Ausführen.**

```bash
./gradlew :frontend:e2eTest                           # alle e2e-Flows (UC-01..06)
./gradlew :frontend:e2eTest -Pe2e.browser=firefox     # andere Engine
./gradlew :frontend:e2eTest --tests "*MissionCreate*" # ein einzelner Flow
./gradlew :frontend:smokeTest                         # nur der Smoke-Subset (UC-07)
```

## Use-Case-Schema

Jedes Dokument folgt demselben Schema: **Akteur**, **Vorbedingungen**, **Auslöser**, **Hauptablauf**, **Erwartetes Ergebnis** und **Sonderfälle & Lehren** — letztere halten die CI-, Timing- und Tenancy-Eigenheiten fest, die der jeweilige Flow beim Aufbau aufgedeckt hat.
