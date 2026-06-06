# UC-07 — Kernseiten-Smoke (nicht-destruktiv)

|                |                                                                                                                           |
|----------------|---------------------------------------------------------------------------------------------------------------------------|
| **ID**         | UC-07                                                                                                                     |
| **Tag**        | `smoke`                                                                                                                   |
| **Testklasse** | [`CorePagesSmokeE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/iri/basetool/frontend/e2e/CorePagesSmokeE2eTest.java) |

## Akteur

Authentifizierter User. Gegen Staging der dort konfigurierte Test-User (Credentials aus CI-Secrets), ephemer der `test-admin`.

## Vorbedingungen

- Eingeloggte Session (einmaliger Login im `@BeforeAll`).
- **Kein** Daten-Seeding — der Flow ist rein lesend.

## Auslöser

Nach dem Login wird jede Kernseite einzeln aufgerufen (parametrisierter Test über die Pfadliste).

## Hauptablauf

Für jeden Pfad in `/`, `/missions`, `/orders`, `/refinery-orders`, `/hangar`:

1. Navigiere mit der authentifizierten Session zu `<baseUrl><pfad>`.
2. Prüfe, dass die authentifizierte App-Shell rendert: der Sidebar-Link `nav-orders` ist sichtbar.

## Erwartetes Ergebnis

Jede Kernseite rendert für einen eingeloggten User die App-Shell — keine Weiterleitung zum Identity Provider, kein Fehler.

## Sonderfälle & Lehren

- **Nicht-destruktiv & ziel-agnostisch:** Der Flow erzeugt/ändert nichts und ist daher gefahrlos gegen ein geteiltes Staging-Deployment einsetzbar. Er läuft sowohl gegen den ephemeren Stack als auch (mit gesetztem `E2E_BASE_URL`) gegen Staging.
- **Assertion-Ziel `nav-orders`:** dieser Sidebar-Link ist hinter `isAuthenticated()` gegated und damit ein stärkeres Login-Signal als das auch anonym sichtbare `nav-missions`.
- **CI:** Der Smoke-Subset läuft in einem eigenen Workflow (`e2e-smoke.yml`) auf `schedule` + `workflow_dispatch` gegen Staging — nicht als PR-Check. Lokal/ephemer braucht WebKit den Hosts-Eintrag `127.0.0.1 host.docker.internal` (siehe den Cross-Browser-Abschnitt im [Projekt-README](../../README.md)).

