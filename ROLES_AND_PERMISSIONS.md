# Rollen- und Rechte-Matrix (Profit Basetool)

> **Stand 2026-06-02 (nach Auftrags-Umbau #340, Operations/Auszahlungen, Material-Claims, Personal-Blueprints, Blueprint-Verfügbarkeit #364).**
> Diese Matrix wurde gegen die tatsächliche Implementierung verifiziert:
> die `@PreAuthorize`-Annotationen aller ~50 Backend-Controller, die
> URL-Matrix in
> [`backend/.../config/SecurityConfig.java`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/SecurityConfig.java)
> und
> [`frontend/.../config/SecurityConfig.java`](frontend/src/main/java/de/greluc/krt/iri/basetool/frontend/config/SecurityConfig.java),
> die Rollen-Seeds in
> [`DataInitializer`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/DataInitializer.java)
> und der Authority-Konverter
> [`CustomJwtGrantedAuthoritiesConverter`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/CustomJwtGrantedAuthoritiesConverter.java).
> **Bei Abweichungen zwischen diesem Dokument und dem Code zählt immer der
> Code** (`@PreAuthorize` + `SecurityConfig`).

Dieses Dokument fasst zusammen, **wer was darf** — von völlig anonymen,
nicht angemeldeten Besuchern bis zum Administrator.

---

## 0. Zwei Durchsetzungsebenen (wichtig zum Lesen der Matrix)

Jeder Request durchläuft **zwei** voneinander unabhängige Gates. Ein Zugriff
ist nur erlaubt, wenn er **beide** passiert:

1. **URL-Matrix (`SecurityConfig.authorizeHttpRequests`)** — das äußere Tor.
   Legt pro Pfad fest: `permitAll()` (auch anonym), `authenticated()` oder
   eine konkrete Rolle. Wird *zuerst* ausgewertet.
2. **Method-Level `@PreAuthorize`** auf Controller/Service — das innere Tor.
   Verfeinert über Spring-Security-SpEL, häufig mit den Beans
   `@ownerScopeService`, `@missionSecurityService`,
   `@specialCommandSecurityService`.

Die beiden Ebenen können sich nur **verschärfen, nie aufweichen**:

- URL `authenticated()` schlägt Method `permitAll()` → der Endpunkt ist
  *nicht* anonym erreichbar, auch wenn die Methode `permitAll()` trägt
  (z. B. `/api/v1/system/ping`).
- URL `permitAll()` + Method `isAuthenticated()` → effektiv **angemeldet
  erforderlich** (z. B. *Mission anlegen*, `POST /api/v1/missions`).

Wer eine Berechtigung beurteilt, muss daher **beide** Ebenen lesen.

---

## 1. Anonyme (nicht angemeldete) Nutzer

Das Basetool hat eine bewusst öffentliche Fläche, damit Auftraggeber und
Gäste **ohne Login** mit der Organisation interagieren können. Im Frontend
sind dafür die Routen `/`, `/missions/**`, `/operations/**`, `/orders/**`,
die Rechtsseiten (`/impressum`, `/privacy`, `/terms`) und statische Assets
auf `permitAll()` gesetzt; im Backend eine explizit aufgezählte Liste von
`permitAll()`-Endpunkten. Alles andere erfordert eine Anmeldung.

### 1.1 Was anonyme Nutzer dürfen

| Fähigkeit | Endpunkt(e) | Gate |
| :--- | :--- | :--- |
| **Stammdaten lesen** (Materialien, Locations, Schiffstypen, Hersteller, Refining-Methoden, Sternensysteme, Job-Typen, Frequenztypen, System-Settings, Staffel-Liste) | `GET /api/v1/{materials,locations,ship-types,manufacturers,refining-methods,star-systems,job-types,frequency-types,settings,squadrons}/**` | URL `permitAll`, kein Method-Gate |
| **Einsätze (Missionen) durchblättern** — nur **nicht-interne** Missionen, Detailansicht inklusive | `GET /api/v1/missions`, `/search`, `/next`, `/{id}` | `@ownerScopeService.canSeeMission` (intern = unsichtbar) |
| **Warenauftrag anlegen** (Material-Auftrag) | `POST /api/v1/orders` | `permitAll()` |
| **Item-Auftrag anlegen** (Fertigteil-Bestellung mit auto-abgeleiteten Materialien) | `POST /api/v1/orders/items` | `permitAll()` |
| **Bestellbaren Item-Katalog durchsuchen** | `GET /api/v1/orders/item-catalog/**` | `permitAll()` |
| **Sich bei einem (nicht-internen) Einsatz als Gast anmelden** — mit frei wählbarem `guestName` | `POST /api/v1/missions/{id}/participants/add`, `/participants/slim` | `@ownerScopeService.canSeeMission` |
| **Ein-/Auschecken** beim Einsatz | `POST /api/v1/missions/{id}/participants/{pid}/check-in[/slim]`, `…/check-out[/slim]` | `@missionSecurityService.canAccessParticipant` |
| **Eigenen Gast-Teilnehmer bearbeiten** (Job-Typ, Schiff, Kommentar, Zeiten) | `PUT /api/v1/missions/{id}/participants/{pid}[/slim]` | `canAccessParticipant` |
| **Auszahlungsart ändern** (Auszahlungspräferenz, z. B. `DONATE`) | `PUT /api/v1/missions/{id}/participants/{pid}/payout-preference[/slim]` | `canAccessParticipant` |
| **Eigenen Gast-Teilnehmer entfernen** | `DELETE /api/v1/missions/{id}/participants/{pid}[/slim]` | `canAccessParticipant` |
| **Finanz-Eintrag zu einem sichtbaren Einsatz erfassen** | `POST /api/v1/finance-entries` | `@ownerScopeService.canSeeMission(#dto.missionId)` |

**Warum die Teilnehmer-Endpunkte anonym funktionieren:** Ein **Gast-Teilnehmer
ist nicht mit einem Benutzerkonto verknüpft** (`participant.user == null`).
[`MissionSecurityService.canAccessParticipant`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/MissionSecurityService.java)
gibt für solche unverknüpften Teilnehmer **`true` für jeden** zurück — das ist
die bewusste Konstruktionsnaht, die den Anmelde-Flow ohne Login nutzbar macht.
Sobald ein Teilnehmer mit einem echten User verknüpft ist, dürfen nur noch
dieser User selbst oder eine erhöhte Rolle (Mission-Manager/Officer/Admin) ihn
bearbeiten.

**Wohin anonyme Aufträge laufen:** Beim Anlegen ohne Login wird der Auftrag
zwingend auf das konfigurierte **Intake-Spezialkommando** gestempelt
(System-Setting `job_order.intake_special_command_id`, eingeführt mit V128).
So landet jeder Gast-Auftrag in einer definierten SK-Warteschlange statt im Nichts.

### 1.2 Was anonyme Nutzer **nicht** dürfen

- **Einsätze/Operations anlegen oder verwalten** — `POST /api/v1/missions`
  ist zwar URL-`permitAll`, aber method-`isAuthenticated()` → Login nötig.
  Operations (`/api/v1/operations/**`) sind komplett angemeldet.
- **Die Auftrags-Liste oder Auftrags-Details sehen** — `GET /api/v1/orders`
  und `/orders/{id}` fallen unter `isAuthenticated()` + `canSeeJobOrder`. Ein
  Gast kann also einen Auftrag *absenden*, ihn danach aber nicht
  weiterverfolgen.
- **Material-Claims, Refinery, Hangar, Lager, Persönliches Inventar/Blueprints,
  Benutzerverzeichnis, Promotion-System, Admin-Bereich** — alles angemeldet
  bzw. rollen-gegated.

### 1.3 Datenschutz für Gäste (`cleanupForGuest`)

Alle öffentlichen Mission-Antworten werden für nicht-angemeldete Aufrufer
serverseitig bereinigt (`cleanupMissionForGuest` / `cleanupParticipantForGuest`
in [`MissionController`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/controller/MissionController.java)):
interne Missionen verschwinden komplett, Manager-Listen, Finanzdaten,
Refinery-Bezüge und Owner-Felder werden geleert, und bei Teilnehmern werden
E-Mail, Realname und Rollen entfernt — sichtbar bleiben nur `guestName` /
Callsign. **Namen, E-Mails und Tokens landen nie in einer Gast-Antwort.**

> **Anonym ≠ Rolle „Guest".** „Anonym" = gar nicht eingeloggt (kein JWT),
> erreicht nur die `permitAll`-Liste oben. Die **Rolle `GUEST`** dagegen ist
> ein *angemeldeter* Keycloak-User ganz ohne Authorities (siehe §2): er
> passiert `isAuthenticated()`-Gates (sieht also z. B. sein eigenes — leeres —
> Inventar, die Auftrags-Liste, Operations), scheitert aber an jedem
> `hasRole(...)`/`hasAuthority(...)`-Check.

---

## 2. Rollen & Basis-Berechtigungen

Rollen werden aus den Keycloak-Realm-Rollen abgeleitet (`ROLE_<GROSS_SNAKE>`)
und im
[`DataInitializer`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/DataInitializer.java)
mit Authorities geseedet. Zusätzlich gilt eine **Rollen-Hierarchie**.

### Rollen-Hierarchie (backend + frontend identisch)
```
ROLE_ADMIN   > ROLE_LOGISTICIAN
ROLE_OFFICER > ROLE_LOGISTICIAN
ROLE_ADMIN   > ROLE_MISSION_MANAGER
ROLE_OFFICER > ROLE_MISSION_MANAGER
```
Admins und Officer erfüllen damit automatisch jeden `LOGISTICIAN`- und
`MISSION_MANAGER`-Check.

### Geseedete Authorities

| Rolle | Authorities (DataInitializer) |
| :--- | :--- |
| **Admin** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE` (+ `LOGISTICIAN`/`MISSION_MANAGER` via Hierarchie) |
| **Officer** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE` (+ `LOGISTICIAN`/`MISSION_MANAGER` via Hierarchie) |
| **Squadron Member** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ` |
| **Guest** | *(keine — leeres Set)* |

`USER_MANAGE` bleibt aus historischen Gründen im Officer-Set, wird aber von
keinem Endpunkt mehr geprüft (effektiv inert — alle Member-Management-Endpunkte
sind seit dem Phase-4-Lockdown `hasRole('ADMIN')`).

### Kontextuelle Rollen aus OrgUnit-Mitgliedschaften

`LOGISTICIAN` und `MISSION_MANAGER` sind **keine** Keycloak-Rollen, sondern
**Flags pro OrgUnit-Mitgliedschaft** (`org_unit_membership.is_logistician` /
`is_mission_manager`). Der
[`CustomJwtGrantedAuthoritiesConverter`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/CustomJwtGrantedAuthoritiesConverter.java)
befördert daraus zwei Authority-Flächen:

- **Flach** `ROLE_LOGISTICIAN` / `ROLE_MISSION_MANAGER`, sobald **irgendeine**
  Mitgliedschaft (Staffel oder SK) das Flag trägt — damit funktionieren alle
  bestehenden `hasRole('LOGISTICIAN')`-Gates.
- **Kontextuell** `ROLE_LOGISTICIAN@<orgUnitId>` pro (Mitgliedschaft, Flag)-Paar
  — damit das per-OrgUnit-Scoping am `@PreAuthorize`-Aufrufort
  (`@ownerScopeService.canEdit…`) ohne Service-Roundtrip aufgelöst werden kann.

> Die **alten** `app_user.is_logistician` / `app_user.is_mission_manager`-Spalten
> wurden mit **V101** entfernt — Quelle der Wahrheit ist ausschließlich
> `org_unit_membership`. Mitgliedschaftslose Accounts (Admins, Gäste) tragen
> kein Logistician-/Mission-Manager-Flag.

### SK-Lead (Sonderfall)

Eine Mitgliedschaft mit `is_lead = true` (gibt es per DB-CHECK nur auf
**Spezialkommando**-Zeilen) macht den User innerhalb *dieses einen SK*
automatisch **sowohl `LOGISTICIAN` als auch `MISSION_MANAGER`** (flach +
kontextuell) — der Lead steht über beiden Rollen seines SK, analog dazu, dass
ein Officer Logistician + Mission-Manager seiner eigenen Staffel ist. Zusätzlich
darf ein Lead die **Mitglieder seines SK verwalten** (hinzufügen/entfernen/Flags
`is_logistician`/`is_mission_manager` togglen) über
`@specialCommandSecurityService.canManageMembers`. Das **Lead-Flag selbst** zu
setzen bleibt **Admin-only** (Lead kann sich nicht selbst eskalieren). Kein
Carry-over auf andere SKs.

---

## 3. Zugriffsmatrix nach Funktionsbereich

Spalten: **Anonym** = nicht eingeloggt · **Member** = Squadron Member ·
**Log.** = Member + Logistician-Flag · **MM** = Member + Mission-Manager-Flag ·
**Officer** · **Admin**.

> Logistician/Mission-Manager sind **Zusatz-Flags** auf einem Member: sie erben
> alle Member-Rechte und addieren ihre flag-spezifische Fähigkeit, **gescopt auf
> ihre OrgUnit(s)** (`@ownerScopeService.canEdit…`). Officer ⊇ Log.+MM der
> **eigenen Staffel**; Admin ⊇ alles, staffelübergreifend.

### 3.1 Auth / Kontext

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Angemeldet sein (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Eigenes Profil / `GET /me`, aktiver OrgUnit-Kontext (`/me/active-org-unit`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Benutzerverzeichnis lesen (`/users`, `/search`, `/lookup`, `/{id}`, `/{id}/memberships`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |

### 3.2 Hangar & Persönliche Daten

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Hangar lesen (`HANGAR_READ`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Eigene Schiffe pflegen / Import (CCU, HangarXPLOR, StarJump) (`isAuthenticated()` + Owner) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Schiffe anderer Member verwalten (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `resetAllFittedStatus` (`hasAnyRole('ADMIN','OFFICER')`) | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Persönliches Inventar / Persönliche Blueprints (eigene) (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Persönl. Inventar/Blueprints **anderer** verwalten (`/admin/...`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Blueprint-Verfügbarkeit der Orgeinheit lesen (`/blueprint-overview`, `canAccessBlueprintOverview`) | ❌ | ❌ | ❌¹ | ❌ | ✅ | ✅ |

¹ SK-Leads sehen die Übersicht zusätzlich **für ihre SK** (über das `is_lead`-Flag, nicht über das reine Logistician-Flag). Officer sehen nur ihre Staffel; Admins ohne Pin alle Orgeinheiten, mit Pin nur die angepinnte.

### 3.3 Lager (Inventory) & Aufträge (Job Orders)

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Lager-View ansehen (`/inventory`, Member+) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Lager bearbeiten / aus-/einbuchen (`isAuthenticated()` + `canEditInventoryItem`, Owner-Scope) | ❌ | ✅¹ | ✅ | ✅¹ | ✅ | ✅ |
| Auftrag **anlegen** (Material- & Item-Auftrag) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Auftrags-Liste / -Detail lesen (`isAuthenticated()` + `canSeeJobOrder`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Auftrag **bearbeiten** (Status, Priorität, Materialien, Handover) (`hasRole('LOGISTICIAN')` + `canEditJobOrder`) | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| Verantwortliche Einheit umsetzen (`PATCH /{id}/responsible-org-unit`) | ❌ | ❌ | ✅² | ❌ | ✅² | ✅ |
| Material-Claims auf SK-Aufträgen eintragen/zurückziehen (`hasRole('LOGISTICIAN')`) | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |
| Auftrag **löschen** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

¹ Nur über das eigene Lager / die Owner-Scope-Prüfung — nicht generell.
² Admin frei; Staffel-Logistiker/-Officer nur **Eskalation** des eigenen
Staffel-Auftrags an ein SK.

### 3.4 Refinery

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Eigene Refinery-Orders lesen/anlegen (`isAuthenticated()` [+ `canSeeRefineryOrder`]) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Refinery-Order bearbeiten/löschen/lagern (`isAuthenticated()` + `canEditRefineryOrder`: Owner **oder** Logistician) | ❌ | ✅¹ | ✅ | ✅¹ | ✅ | ✅ |
| Refinery-Orders **für andere** anlegen/verwalten (`/users/{id}`, `hasRole('LOGISTICIAN')`) | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ |

¹ Nur als Owner der jeweiligen Order.

### 3.5 Einsätze (Missionen)

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Nicht-interne Missionen lesen (`canSeeMission`, gast-bereinigt) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mission **anlegen** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Als (Gast-)Teilnehmer anmelden / ein-/auschecken / Auszahlungsart ändern / abmelden (`canAccessParticipant`) | ✅¹ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Mission **verwalten** (bearbeiten, Teilnehmer/Units/Crew/Frequenzen, Party-Lead) (`canManageMission`) | ❌ | ✅² | ✅² | ✅³ | ✅³ | ✅ |
| Manager / Owner setzen (`canManageManagers` / `canChangeOwner`) | ❌ | ✅² | ✅² | ✅² | ✅³ | ✅ |
| Mission **löschen** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

¹ Anonym nur auf **unverknüpften Gast-Teilnehmern**; eingeloggte User nur auf
ihrem eigenen verknüpften Teilnehmer. ² Nur als **Owner/Co-Manager** der Mission.
³ Mission-Manager/Officer zusätzlich nur im eigenen Staffel-Scope
(`canEditMission`).

### 3.6 Operations (Einsatz-Klammer, Finanzen & Auszahlungen)

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Operations lesen (Liste/Detail/Finanzen/Auszahlungen) (`isAuthenticated()` [+ `canSeeOperation`]) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Operation anlegen/bearbeiten (`hasRole('MISSION_MANAGER')` [+ `canEditOperation`]) | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| Auszahlung als **paid-out markieren** (`hasRole('MISSION_MANAGER')` + `canEditOperation`) | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| paid-out **zurücknehmen** (zusätzlich `hasAnyRole('ADMIN','OFFICER')`) | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Operation **löschen** (`hasRole('ADMIN')` + `canEditOperation`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

> Asymmetrie der Auszahlung: jeder Mission-Manager darf `paidOut=true` setzen,
> aber nur Officer/Admin dürfen ein bestätigtes paid-out wieder auf `false`
> zurücksetzen.

### 3.7 Mission-Finanzen & Profit

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Finanz-Einträge einer Mission lesen (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Finanz-Eintrag **anlegen** (`canSeeMission`, gast-bereinigt) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Finanz-Eintrag bearbeiten/löschen (`canEditFinanceEntry`: Owner **oder** Officer/Admin) | ❌ | ✅¹ | ✅¹ | ✅¹ | ✅ | ✅ |
| Profit-Kalkulation lesen (`hasAnyRole('SQUADRON_MEMBER','MEMBER','OFFICER','ADMIN')`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Material-Übersicht / Material-Collection eines Auftrags (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |

¹ Nur eigener Eintrag und nur solange weiterhin Teilnehmer der Mission.

### 3.8 Promotion-System (Beförderung)

Alle Promotion-Controller sind class-level `isAuthenticated()`; das
**ADMIN-oder-Officer-der-eigenen-Staffel**-Gate sitzt in der Service-Schicht
(`canEditSquadron(topic.owningSquadron.id)`).

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Themenbereiche/Kategorien/Level-Inhalte/Rangvoraussetzungen lesen (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| …**pflegen** (Service: Admin **oder** Officer der besitzenden Staffel) | ❌ | ❌ | ❌ | ❌ | ✅¹ | ✅ |
| Eigene Bewertungen / Eligibility ansehen (`/my`, JWT-Sub) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Bewertungen/Eligibility **anderer** ansehen, Member-Liste (`hasAnyRole('ADMIN','OFFICER')`, Officer staffel-gescopt) | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Promotion-Subsystem je Staffel an-/abschalten (`PATCH /squadrons/{id}/promotion-enabled`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

¹ Nur für die eigene Staffel. **SKs sind vom Promotion-System per
DB-CHECK/Trigger + ArchUnit-Regel dauerhaft ausgeschlossen.**

### 3.9 Organisation (Staffeln & Spezialkommandos)

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Staffel-Liste lesen | ✅¹ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SK-Liste lesen (`isAuthenticated()`; inaktive nur Admin) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Aktiven OrgUnit-Kontext umschalten (Sidebar-Switcher) | ❌ | ✅² | ✅² | ✅² | ✅² | ✅ |
| Staffel-Lifecycle (anlegen/umbenennen/löschen/aktivieren, `promotion-enabled`, `profit-eligible`) (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Staffel-Mitglieds-Flags setzen (`PATCH /squadrons/{id}/members/{uid}`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| SK-Lifecycle (anlegen/umbenennen/löschen/aktivieren, `profit-eligible`) (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| SK-**Mitglieder** verwalten (add/remove/Flags) (`@specialCommandSecurityService.canManageMembers`) | ❌ | ❌ | ❌ | ❌ | ❌³ | ✅ |
| SK-**Lead-Flag** setzen (`PATCH /special-commands/{id}/members/{uid}/lead`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

¹ Stammdaten-Read, anonym erlaubt. ² Nicht-Admins schalten zwischen ihren
Mitgliedschaften; Admins zusätzlich „Alle Staffeln". ³ SK-Mitgliederverwaltung
ist **Admin oder SK-Lead dieses SK** — nicht an die globale Officer-Rolle
gebunden.

### 3.10 Stammdaten, Ankündigungen, System

| Funktion (Gate) | Anonym | Member | Log. | MM | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Öffentlich** lesbare Stammdaten (Materialien, Locations, Schiffstypen, Hersteller, Sternensysteme, Refining-Methoden, Frequenz-/Job-Typen, Staffeln, System-Settings) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Angemeldet** lesbare Stammdaten (Terminals, Material-Kategorien) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Admin-only** Stammdaten – auch zum Lesen (Städte, Raumstationen, Outposts, POIs, Material-Aliase, Blueprints) (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Stammdaten **schreiben** (anlegen/ändern/löschen/Sichtbarkeit/Overrides) (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| UEX-Location-Typeahead / Blueprint-Produkt-Suche (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ankündigung **lesen** (`GET /announcement`, `isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ankündigung **schreiben/löschen** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Sync-Reports lesen/aufräumen (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| System-Setting schreiben (`PUT /settings/{key}`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Rollen-/Rechteverwaltung, Member-Attribute/Rang, Flag-Vergabe (`/admin/**`, `/users/*/...`, `hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

Welche Stammdaten anonym lesbar sind, legt allein die `permitAll`-Liste in
`SecurityConfig` fest (siehe §1.1) — alles andere ist mindestens angemeldet,
einige Tabellen (Städte, Stationen, Outposts, POIs, Aliase, Blueprints) sind
schon zum **Lesen** Admin-only. **Schreiben ist bei allen Stammdaten
Admin-only.**

---

## 4. Mehr-OrgUnit-Sichtbarkeit (Scoping)

Lese- und Schreibpfade werden über
[`OwnerScopeService`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/OwnerScopeService.java)
gefiltert (früher `SquadronScopeService`; deckt heute Staffeln **und**
Spezialkommandos ab). Grundregel: Nicht-Admins sehen die Vereinigung ihrer
Mitgliedschaften; Admins ohne aktiven Pin sehen alles, mit Pin dieselbe
restriktive Sicht wie ein Member.

- **Strikt staffel-gescopt** (kein Cross-Staffel): `Ship`, `InventoryItem`
  (Lager-View), `RefineryOrder`, `Operation` — Listen filtern auf
  `owning_org_unit_id`, Detail-/Schreib-Endpunkte gaten auf
  `canSee*`/`canEdit*`.
- **Cross-Staffel mit Public-Escape**: `Mission` — für andere OrgUnits sichtbar
  genau dann, wenn `is_internal = false`; editierbar nur durch die besitzende
  OrgUnit + Admins.
- **Bedingt staffel-gescopt**: `JobOrder` (+ `JobOrderMaterial` /
  `JobOrderHandover` / `MaterialClaim`). Ein Auftrag trägt
  `responsible_org_unit_id` (die **bearbeitende** Einheit — steuert die
  Sichtbarkeit, nur über `PATCH /{id}/responsible-org-unit` änderbar) und
  `requesting_org_unit_id` (der **Auftraggeber** — gewährt **keine**
  Sichtbarkeit). Verantwortlich = **SK** → öffentlich für alle Staffeln
  (gemeinsame Warteschlange, an die sich Staffeln per Material-Claim melden);
  verantwortlich = **Staffel** → privat für diese Staffel + Admins. SK-Auftrags-*Edits*
  laufen über das Rollen-Gate (Logistician+), nicht über das Staffel-Scope.
- **Oversight-Übersicht** (kein eigenes Aggregat): Die Blueprint-Verfügbarkeit
  (`/blueprint-overview`) aggregiert die per-Nutzer-`personal_blueprint`-Zeilen über
  die Mitglieder der Orgeinheiten, die der Aufrufer **beaufsichtigt** — Officer ihre
  Staffel, SK-Leads ihre SK(s), Admins alle bzw. die angepinnte
  (`OwnerScopeService.currentBlueprintOversightScope()`, enger als die
  Mitgliedschafts-Vereinigung der normalen Listen). Besitzer werden nur als
  Anzeigename ausgeliefert, nie als Sub/E-Mail.

---

## 5. Besonderheiten der Implementierung

1. **Keycloak-Sync / Fallback:** Lassen sich JWT-Claims (`realm_access.roles`)
   nicht vollständig synchronisieren, fällt das System auf die reinen
   Rollen-Namen aus dem Token zurück (Präfix `ROLE_`, Großbuchstaben,
   Leerzeichen → `_`).
2. **Default-Rolle:** Wird keine bekannte Rolle übermittelt, erhält der
   Benutzer **Guest** (keine Authorities).
3. **Ränge:** Die `UserService`-Logik gibt vor, dass `OFFICER` nur Ränge 1–12,
   `SQUADRON_MEMBER` Ränge 13–20 erhalten dürfen.
4. **Logistician-/Mission-Manager-Flags** werden ausschließlich von **Admins**
   über die Mitgliedschaftsverwaltung (`org_unit_membership`) vergeben
   (`UserController#patchLogistician` / `#patchMissionManager` und die
   SquadronMembership-/SpecialCommandMembership-Endpunkte sind `hasRole('ADMIN')`
   bzw. für SK zusätzlich der SK-Lead über `canManageMembers`). Die alten
   `app_user`-Flag-Spalten existieren seit V101 nicht mehr.
5. **Phase-4-Lockdown:** Der gesamte Admin-Bereich (Stammdaten,
   Member-Management, Ankündigungen, UEX, System-Settings,
   SK-/Staffel-Lifecycle) ist `hasRole('ADMIN')`. Officer behalten ihre
   staffel-internen Funktionen (Mission-Management, Hangar-Write inkl.
   `resetAllFittedStatus`, Refinery, Logistician via Hierarchie, der
   Auftrags-Workflow und — als einzige Officer-Carve-outs — Promotion-Pflege der
   eigenen Staffel sowie SK-Mitgliederverwaltung **nur** als SK-Lead).
6. **Architektur-Guards (ArchUnit):** Jeder `@RestController` trägt mindestens
   ein `@PreAuthorize`; staffel-gescopte Services müssen `OwnerScopeService` /
   `AuthHelperService` injizieren; Controller geben nie JPA-Entities zurück. Ein
   neuer Verstoß bricht den Build (`./gradlew test`).
