# UC-06 — Job-Order-Handover protokollieren

| | |
|---|---|
| **ID** | UC-06 |
| **Tag** | `e2e` |
| **Testklasse** | [`JobOrderHandoverE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/iri/basetool/frontend/e2e/JobOrderHandoverE2eTest.java) |

## Akteur
Authentifizierter User mit der Rolle LOGISTICIAN, OFFICER oder ADMIN (IRIDIUM-Mitgliedschaft).

## Vorbedingungen
Die komplette Vorbedingungskette wird per REST geseedet:

- IRIDIUM-Mitgliedschaft (`ensureIridiumMembership`).
- Ein Job-Order-Material (`ensureJobOrderMaterial`) und eine Location (`createLocation`).
- Ein Job Order, der dieses Material anfragt (`createJobOrder`).
- Ein dem Auftrag **verknüpfter Lagereintrag** (`createInventoryItemForJobOrder`: `jobOrderId` gesetzt, gleiches Material, Qualität ≥ `minQuality`).

## Auslöser
Der User öffnet das Handover-Modal auf der Auftragsdetailseite `/orders/{id}`.

## Hauptablauf
1. Navigiere zu `/orders/{jobOrderId}`.
2. Öffne das Modal über `order-handover-open`; das Modal lädt das verknüpfte Inventar **lazy per `fetch`** (`/orders/{id}/materials/{matId}/inventory`). Warte per `waitForResponse` auf diese Antwort, **bevor** eine Zeile hinzugefügt wird.
3. Füge eine Materialzeile hinzu (`add-handover-item-btn`), wähle den Lagereintrag (`items[0].inventoryItemId`) und die Menge (`items[0].amount`).
4. Fülle Übergabezeit (`.date-part` / `.time-part` im Modal) und Empfänger (`#recipientHandle`).
5. Speichern über `order-handover-submit`.

## Erwartetes Ergebnis
Die Übergabe erscheint in der Handover-Tabelle des Auftrags als `order-handover-row` mit dem Empfänger.

## Sonderfälle & Lehren
- **Concurrency-/409-sensibel:** Der Handover dekrementiert den verknüpften Lagereintrag **und** die offene Menge des Job-Order-Materials in **einer** Transaktion (`*WithinTransaction`-Pattern). Playwrights Auto-Waiting deckt das AJAX-/`data-version`-Timing ab.
- **Snapshot-Dropdown:** Die „Material auswählen"-Schaltfläche snapshottet das gecachte Inventar **beim Klick** in das Dropdown. Wird vor dem `fetch` geklickt, bleibt das Dropdown leer und füllt sich nicht nach → erst auf die `…/inventory`-Antwort warten.
- **Strikte CSP:** `page.waitForFunction(String)` wird im Seitenkontext per `eval` ausgeführt und von der CSP (`script-src` ohne `unsafe-eval`) geblockt. Stattdessen auf Netzwerk-Antworten / DOM-Zustände warten.
- **`responsibleOrgUnitId` + Profit-Eligibility:** `POST /api/v1/orders` verlangt ein `responsibleOrgUnitId`, das auf eine **profit-eligible** Einheit auflöst (sonst 400; `creatingSquadronId` ist entfallen). Der Stack-Bootstrap schaltet IRIDIUM einmalig profit-eligible (vor dem ersten Seitenaufruf, der den Squadron-Katalog-Cache füllt); `createJobOrder` setzt IRIDIUM als responsible + requesting.
- Übergabezeit nutzt denselben `datetime-split-group`-Mechanismus wie UC-02, hier aber ohne Race (das Modal öffnet lange nach dem Page-Load) und mit `data-validate-not-past='false'`.
