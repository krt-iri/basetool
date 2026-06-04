# UC-12 — Anonymer Auftrag anlegen (Auftraggeber & bearbeitende Einheit)

| | |
|---|---|
| **ID** | UC-12 |
| **Tag** | `e2e` |
| **Testklasse** | [`AnonymousJobOrderE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/iri/basetool/frontend/e2e/AnonymousJobOrderE2eTest.java) |

## Akteur
Anonymer (nicht angemeldeter) Gast über das öffentliche Anfrageformular.

## Vorbedingungen
- Keine Session — `/orders/create` ist `permitAll`.
- **Profit-Spezialkommando** (`createSpecialCommand` + `setSpecialCommandProfitEligible`), zugleich als **Eingangs-SK** gesetzt (`setSystemSetting` auf `job_order.intake_special_command_id`).
- **Nicht-profit-fähiges Spezialkommando** (`createSpecialCommand`, ohne Profit-Flag) — taucht nur als Auftraggeber auf.
- IRIDIUM-Staffel profit-berechtigt (im Stack-Bootstrap geseedet).
- Ein job-order-fähiges Material (`ensureJobOrderMaterial`).
- Ein bestellbares Item — `game_item` + aktiver `blueprint` mit aufgelöster RESOURCE-Zutat — geseedet **im Stack-Bootstrap** (`seedOrderableItem`), damit der frontend-gecachte Item-Katalog beim ersten Aufruf gefüllt ist.

## Auslöser
Der Gast öffnet `/orders/create`.

## Hauptablauf

### Materialauftrag (UI)
1. Navigiere zu `/orders/create`.
2. Prüfe die Picker: „Auftraggeber" (`#requestingOrgUnitId`) listet **alle** Org-Einheiten inkl. der nicht-profit-fähigen SK; „bearbeitende Einheit" (`#responsibleOrgUnitId`) listet **nur** profit-fähige (Profit-SK + IRIDIUM, **nicht** die nicht-profit-fähige SK) und ist auf die Eingangs-SK **vorbelegt**.
3. Wähle einen Auftraggeber (hier die nicht-profit-fähige SK) und überschreibe die bearbeitende Einheit mit der IRIDIUM-Staffel.
4. Fülle Handle, Materialzeile (`order-material-select` / `order-material-amount`) und sende über `order-submit`.

### Item-Auftrag (UI)
1. Navigiere zu `/orders/create`, schalte auf Item-Modus (`order-mode-item`).
2. Prüfe, dass die bearbeitende Einheit des Item-Formulars (`#item-responsibleOrgUnitId`) auf die Eingangs-SK vorbelegt ist.
3. Wähle einen Auftraggeber (`#item-requestingOrgUnitId` → IRIDIUM), belasse die bearbeitende Einheit auf der Eingangs-SK.
4. Wähle das Item (`select[data-role='item-select']`) und **warte**, bis die Bauplan-/Material-Ableitung die erste Materialzeile (`items[0].materials[0].materialId`) erzeugt hat — erst dann trägt die Zeile die nötige Bauplan-ID.
5. Sende über `order-item-submit`.

### Fallback-Kanten (direkte anonyme API)
- POST `/api/v1/orders` mit einer **nicht-profit-fähigen** bearbeitenden Einheit → Auftrag läuft auf die Eingangs-SK.
- POST `/api/v1/orders` **ohne** bearbeitende Einheit → Auftrag läuft auf die Eingangs-SK.

## Erwartetes Ergebnis
- Beide UI-Aufträge werden angelegt; ein Admin-Read-back (`findOrderByHandle`) bestätigt Typ (`MATERIAL`/`ITEM`), die gewählte bzw. vorbelegte **bearbeitende Einheit** und den gewählten **Auftraggeber**.
- Die API-Kanten liefern eine 2xx-Antwort, deren `responsibleOrgUnit` auf die Eingangs-SK zeigt und deren `requestingOrgUnit` den übergebenen Auftraggeber trägt.

## Sonderfälle & Lehren
- **Picker-Quelle.** Beide Picker stammen aus einem einzigen, anonym erreichbaren `GET /api/v1/org-units/active` (jede Option trägt ihr `isProfitEligible`-Flag). Da diese Liste **nicht** frontend-gecacht ist, erscheinen frisch geseedete SK sofort — der Test braucht keinen Cache-Workaround für die Picker.
- **Item-Katalog ist gecacht.** Die bestellbaren Items (`getCached`) werden beim ersten `/orders/create`-Aufruf für 10 min gepinnt. Deshalb wird das Item **im Stack-Bootstrap** geseedet (vor jeder Navigation), analog zur Profit-Berechtigung der IRIDIUM-Staffel — sonst pinnt eine andere Testklasse einen leeren Katalog. Der Test wählt „was angeboten wird" (Index 1), nicht ein bestimmtes Item.
- **Vorbelegung = Eingangs-SK.** Die bearbeitende Einheit wird für Gäste auf die konfigurierte Eingangs-SK (`/admin/settings`) vorbelegt — dieselbe Einheit, auf die das Backend einen Gast-Auftrag ohne profit-fähige Wahl routet. So zeigt das Formular vorab die Einheit, auf der der Auftrag tatsächlich landet.
- **Gast darf wählen, aber nur profit-fähig.** Das Backend honoriert eine profit-fähige Wahl des Gastes; eine fehlende, nicht auflösbare oder nicht-profit-fähige Einheit fällt auf die Eingangs-SK zurück — ein Gast kann Arbeit nie an eine nicht-profit-fähige Einheit lenken. Die UI bietet nicht-profit-fähige Einheiten in der bearbeitenden Einheit gar nicht erst an; die Fallback-Kanten sind daher nur per direkter API testbar.
- **Auftraggeber ohne Restriktion.** Jede Org-Einheit (auch eine nicht-profit-fähige SK) darf Auftraggeber sein und wird verbatim übernommen.
- **Read-back nötig.** Ein Gast kann die Auftragswarteschlange nicht lesen (gated). Die UI-Flows prüfen das Ergebnis daher per Admin-Read-back; die anonyme Create-Antwort ist zwar gast-redigiert (ohne `assignees`/`handovers`/`version`), behält aber `responsibleOrgUnit`/`requestingOrgUnit`, weshalb die API-Kanten direkt auf der Antwort prüfen.
- **Staging.** Die seeding-abhängigen Tests laufen nur im ephemeren Modus (`assumeTrue(managesStack)`); gegen ein externes `E2E_BASE_URL` werden sie übersprungen.
