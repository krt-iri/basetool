# UC-03 — Job Order anlegen

|                |                                                                                                                           |
|----------------|---------------------------------------------------------------------------------------------------------------------------|
| **ID**         | UC-03                                                                                                                     |
| **Tag**        | `e2e`                                                                                                                     |
| **Testklasse** | [`JobOrderCreateE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/iri/basetool/frontend/e2e/JobOrderCreateE2eTest.java) |

## Akteur

Authentifizierter User mit IRIDIUM-Mitgliedschaft.

## Vorbedingungen

- Eingeloggte Session (UC-01).
- IRIDIUM-Mitgliedschaft geseedet (`ensureIridiumMembership`).
- Ein job-order-fähiges Material angelegt (`ensureJobOrderMaterial`, `isJobOrder=true`) — das Material-Dropdown des Formulars listet nur solche.

## Auslöser

Der User öffnet das Job-Order-Anlegen-Formular `/orders/create`.

## Hauptablauf

1. Navigiere zu `/orders/create`.
2. Wähle die anfragende OrgUnit (`#requestingOrgUnitId` → IRIDIUM).
3. Fülle den Kontakt-Handle (`#handle`).
4. Wähle in der Materialzeile das Material (`order-material-select`) und die Menge (`order-material-amount`).
5. Submit über `order-submit`.

## Erwartetes Ergebnis

Der Auftrag erscheint in der Liste unter `/orders` als `order-row`.

## Sonderfälle & Lehren

- **Cross-Staffel-Workspace:** Job Orders haben **keinen** OrgUnit-Scope-Filter beim Zugriff — jeder mit Rolle/Berechtigung darf lesen/bearbeiten. Das `requestingOrgUnitId`-Dropdown wird aus den Mitgliedschaften des Aufrufers befüllt.
- Das Material-Dropdown ist auf `isJobOrder=true` gefiltert; deshalb wird ein eigens geseedetes Job-Order-Material gewählt.

