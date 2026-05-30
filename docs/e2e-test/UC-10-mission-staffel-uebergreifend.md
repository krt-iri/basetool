# UC-10 — Öffentliche Mission staffel-übergreifend (Teilnehmer aus anderer Staffel)

| | |
|---|---|
| **ID** | UC-10 |
| **Tag** | `e2e` (geplant) |
| **Basis-Flow** | [UC-02](UC-02-mission-anlegen.md) · Scope-Regeln: [Rollen & Scope](rollen-und-scope.md) |

## Akteure
- **Ersteller (Staffel A)** — legt eine **öffentliche** Mission an (`is_internal = false`).
- **Teilnehmer (Staffel B)** — sieht die Mission und tritt ihr bei.

## Vorbedingungen
- Zwei Staffeln A und B; je ein Test-User mit Mitgliedschaft.

## Auslöser
Staffel A öffnet eine Mission für staffel-übergreifende Teilnahme.

## Hauptablauf
1. **Staffel A** legt unter `/missions/new` eine Mission an und lässt sie **öffentlich** (`is_internal = false`).
2. **Staffel B** öffnet `/missions` und sieht A's öffentliche Mission (Cross-Staffel-Public-Escape).
3. **Staffel B** tritt der Mission als Teilnehmer bei.

## Erwartetes Ergebnis
- Die öffentliche Mission ist für B **sichtbar**; eine **interne** Mission (`is_internal = true`) wäre es nicht.
- B's Teilnahme wird mit seiner **Heimat-Staffel** vermerkt (`MissionParticipant.squadron = B`).
- Finanz-/Beteiligungs-Auswertungen splitten nach `participant.squadron` (A vs. B sichtbar).
- Editieren der Mission bleibt der besitzenden Staffel A (+ Admins) vorbehalten — B kann teilnehmen, aber die Mission nicht bearbeiten.

## Sonderfälle & Lehren
- **Public-Escape ist die einzige Cross-Staffel-Sichtbarkeit für Missionen:** Das Repository-`searchMissions` setzt die Klausel `owning_org_unit.id IN (:memberOrgUnitIds) OR is_internal = false` — interne Missionen bleiben strikt bei der Eigentümer-Staffel.
- **`MissionParticipant.squadron`** ist eine der wenigen grandfatherten `squadron_id`-Referenzen; sie hält fest, *aus welcher* Staffel ein Teilnehmer kommt — Grundlage für staffel-übergreifende Beteiligungsabrechnung.
- Anlegen ist `isAuthenticated()` (jeder Auth-User, auch ein einfaches Mitglied); **Editieren/Verwalten** gaten `canEditMission` / `MissionSecurityService.canManageMission` auf Eigentümer-Staffel + Admins.
