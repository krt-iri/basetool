# UC-08 — Job Order staffel-übergreifend (Staffel A bestellt, Staffel B liefert)

|                |                                                                                                                                       |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------|
| **ID**         | UC-08                                                                                                                                 |
| **Tag**        | `e2e`                                                                                                                                 |
| **Testklasse** | [`CrossStaffelJobOrderE2eTest`](../../frontend/src/e2e/java/de/greluc/krt/iri/basetool/frontend/e2e/CrossStaffelJobOrderE2eTest.java) |
| **Basis-Flow** | [UC-03](UC-03-job-order-anlegen.md) · Scope-Regeln: [Rollen & Scope](rollen-und-scope.md)                                             |

## Akteure

- **User der Staffel A** (z. B. Squadron Member oder Logistician von A) — legt den Job Order an.
- **User der Staffel B** (Squadron Member oder Logistician von B) — verknüpft eigenes, B-besessenes Inventar mit dem Auftrag.

## Vorbedingungen

- Zwei Staffeln A und B existieren; je ein Test-User mit Mitgliedschaft.
- Ein Job-Order-Material (`isJobOrder=true`).
- Staffel B besitzt einen Lagereintrag dieses Materials (Owner = B, Qualität ≥ `minQuality`).

## Auslöser

Staffel A braucht Material, das Staffel B liefern soll.

## Hauptablauf

1. **Staffel A** legt unter `/orders/create` einen Job Order an: `requestingOrgUnitId` = A, Material + Menge. → `creating_org_unit_id = A` (unveränderlich), `requesting_org_unit_id = A` (editierbar).
2. **Staffel B** öffnet den Auftrag (Job Orders sind cross-staffel sichtbar) und verknüpft einen eigenen Lagereintrag damit (`POST /api/v1/inventory` mit `jobOrderId` = A's Auftrag; das Item bleibt B-besessen).
3. **Staffel A** öffnet die Auftragsdetailseite `/orders/{id}` und sieht das von B verknüpfte Material im Auftrags-Kontext.

## Erwartetes Ergebnis

- Der Job Order ist für **beide** Staffeln sicht- und bearbeitbar (Cross-Staffel-Workspace, kein OrgUnit-Filter).
- B's verknüpfter Lagereintrag erscheint **im Auftrags-Kontext** (`findByJobOrderIdOrdered`, ungegated) und zählt auf die offene Menge des `JobOrderMaterial` ein.
- B's Lagereintrag erscheint **nicht** in der Lager-View von Staffel A (`findGlobalByFilters` bleibt gegated) — kein Datenleck.

## Sonderfälle & Lehren

- **Cross-Staffel-Workspace:** Zugriff auf Job Order, verknüpfte Materialien und Handover ist bewusst **nicht** OrgUnit-gefiltert. Genau das ermöglicht „A bestellt, B liefert".
- **Zwei OrgUnit-Referenzen:** `responsible_org_unit_id` (die **bearbeitende** Einheit — muss profit-eligible sein, steuert die Sichtbarkeit) vs. `requesting_org_unit_id` (Auftraggeber, editierbar — akzeptiert jede aktive OrgUnit). Das frühere `creating_org_unit_id` ist entfallen.
- **Profit-Eligibility-Pflicht:** `POST /api/v1/orders` verlangt ein `responsibleOrgUnitId`, das auf eine profit-eligible Einheit auflöst (sonst 400). Der Stack-Bootstrap schaltet IRIDIUM einmalig profit-eligible — siehe UC-06/Seeder.
- **Split-Repository** ist der Kern der Isolation: ungegated im Auftrags-Kontext, gegated in der Lager-View.

