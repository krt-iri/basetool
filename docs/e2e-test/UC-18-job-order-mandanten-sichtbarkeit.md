# UC-18 — Job-Order Mandanten-Sichtbarkeit (wer sieht was)

|                |                                                                                                                                |
|----------------|--------------------------------------------------------------------------------------------------------------------------------|
| **ID**         | UC-18                                                                                                                          |
| **Tag**        | `e2e`                                                                                                                          |
| **Testklasse** | [`JobOrderTenancyE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/profit/basetool/frontend/e2e/JobOrderTenancyE2eTest.java) |
| **Basis-Flow** | [Rollen & Scope](rollen-und-scope.md) · Spec [`org-unit-tenancy.md` REQ-ORG-003](../specs/org-unit-tenancy.md)                 |

## Akteure

- **Officer der Staffel B** (profit-eligible) — `test-officer`, in eine frische profit-eligible Staffel B gehomed.
- **Mitglied der Staffel C** (non-profit) — `test-member`, in eine frische non-profit Staffel C gehomed.

## Vorbedingungen

- Ein profit-eligible SK; ein **SK-verantworteter** Auftrag (öffentlich).
- Ein **IRIDIUM-verantworteter** Auftrag (staffel-privat).
- Staffel B profit-eligible (Officer ist Mitglied), Staffel C non-profit (Member ist Mitglied).

## Auslöser

Officer bzw. Member rufen die Auftragsliste `/orders` bzw. eine Detailseite `/orders/{id}` auf.

## Hauptablauf & Erwartetes Ergebnis

1. **SK-öffentliche Warteschlange:** Officer (Staffel B) öffnet `/orders?scope=all&status=OPEN`.
   - Der **SK-verantwortete** Auftrag ist als `order-row` mit seiner `data-id` **sichtbar**.
   - Der **IRIDIUM-verantwortete** Auftrag ist es **nicht** (staffel-privat; B ist kein Mitglied von IRIDIUM).
2. **Profit-Gate:** Das Member (Staffel C, non-profit) wird von `/orders` **und** von `/orders/{skOrderId}` auf das Anlege-Formular umgeleitet (`order-mode-material` sichtbar) — es darf Aufträge **stellen**, aber die Warteschlange nicht **durchsuchen** und keine Detailseite öffnen.

## Sonderfälle & Lehren

- **Sichtbarkeit über `responsibleOrgUnit.kind` (REQ-ORG-003):** Responsible = SK → **öffentlich** für alle profit-eligible Mitglieder (geteilte SK-Warteschlange); Responsible = Staffel → **privat** für diese Staffel + Admins. Die SK-Public-Escape ist `TYPE(responsibleOrgUnit) = SpecialCommand` in `findScopedJobOrders`. `requestingOrgUnit` (Auftraggeber) gewährt **keine** Sichtbarkeit.
- **Officer-Rolle ≠ Cross-Staffel-Sicht:** Die Officer-Realm-Rolle gibt Bearbeitungs-Capability (Rollen-Gate), aber **keine** staffel-übergreifende Sichtbarkeit — der Scope bleibt mitgliedschaftsbasiert. SK-Auftrags-*Edits* laufen über das Rollen-Gate (LOGISTICIAN+), nicht über den Staffel-Scope.
- **Profit-Gate vor Per-Order:** `canViewJobOrders` (Admin, oder mindestens eine profit-eligible Mitgliedschaft) kurzschließt; ein non-profit Member sieht selbst SK-öffentliche Aufträge nicht und bekommt eine leere Liste bzw. einen Redirect.
- **Shared-DB-robust:** Die Assertions prüfen je Auftrag die eigene `data-id`, sodass von Geschwistertests akkumulierte Aufträge (z. B. Intake-SK-Aufträge aus UC-12) die Erwartung nicht verfälschen.
- **Ergänzung zur Schreib-Matrix:** Wer welche Aktions-Controls (Bearbeiten/Löschen/Handover) auf einem **sichtbaren** Auftrag sieht, deckt [`RolePermissionsE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/profit/basetool/frontend/e2e/RolePermissionsE2eTest.java) ab (Edit = LOGISTICIAN, Löschen = ADMIN, Handover = LOGISTICIAN/OFFICER/ADMIN).

