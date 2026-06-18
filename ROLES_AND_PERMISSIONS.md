# Rollen- und Rechte-Matrix (Profit Basetool)

> **Stand 2026-06-11 (nach Auftrags-Umbau #340, Operations/Auszahlungen, Material-Claims, Personal-Blueprints, Blueprint-Verfügbarkeit #364, Bereichsleitungs-Operationen + Teilnehmer-Sichtbarkeit #500/#501, Bearbeiter-Notizen #520, Blaupausen-Abdeckung bei Item-Aufträgen #526, Raffinerie-Screenshot-Import #439).**
> Diese Matrix wurde gegen die tatsächliche Implementierung verifiziert:
> die `@PreAuthorize`-Annotationen aller 54 Backend-Controller, die
> URL-Matrix in
> [`backend/.../config/SecurityConfig.java`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/SecurityConfig.java)
> und
> [`frontend/.../config/SecurityConfig.java`](frontend/src/main/java/de/greluc/krt/profit/basetool/frontend/config/SecurityConfig.java),
> die Rollen-Seeds in
> [`DataInitializer`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/DataInitializer.java)
> und der Authority-Konverter
> [`CustomJwtGrantedAuthoritiesConverter`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/CustomJwtGrantedAuthoritiesConverter.java).
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

| Fähigkeit                                                                                                                                                                                                                  | Endpunkt(e)                                                                                                                                | Gate                                                                                                                                      |
|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------|
| **Stammdaten lesen** (Materialien, Locations, Schiffstypen, Hersteller, Refining-Methoden, Sternensysteme, Job-Typen, Frequenztypen, System-Settings, Staffel-Liste)                                                       | `GET /api/v1/{materials,locations,ship-types,manufacturers,refining-methods,star-systems,job-types,frequency-types,settings,squadrons}/**` | URL `permitAll`, kein Method-Gate (Ausnahme: die Location-Subreads `/refineries` und `/home-locations` tragen method-`isAuthenticated()`) |
| **Aktive Orgeinheiten lesen** (Name, Kürzel, Art, Profit-Flag — füllt die Auswahlfelder des öffentlichen Auftragsformulars)                                                                                                | `GET /api/v1/org-units/active`                                                                                                             | URL `permitAll` + Method `permitAll()`                                                                                                    |
| **Einsätze (Missionen) durchblättern** — nur **nicht-interne** Missionen, Detailansicht **redigiert** (ohne Beschreibung + PII; Organisation, Teilnehmerliste, Einheiten, Frequenzen, Auszahlungsart sichtbar; siehe §1.3) | `GET /api/v1/missions`, `/search`, `/next`, `/{id}`                                                                                        | `@ownerScopeService.canSeeMission` (intern = unsichtbar)                                                                                  |
| **Warenauftrag anlegen** (Material-Auftrag)                                                                                                                                                                                | `POST /api/v1/orders`                                                                                                                      | `permitAll()`                                                                                                                             |
| **Item-Auftrag anlegen** (Fertigteil-Bestellung mit auto-abgeleiteten Materialien)                                                                                                                                         | `POST /api/v1/orders/items`                                                                                                                | `permitAll()`                                                                                                                             |
| **Bestellbaren Item-Katalog durchsuchen**                                                                                                                                                                                  | `GET /api/v1/orders/item-catalog/**`                                                                                                       | `permitAll()`                                                                                                                             |
| **Sich bei einem (nicht-internen) Einsatz als Gast anmelden** — mit frei wählbarem `guestName`                                                                                                                             | `POST /api/v1/missions/{id}/participants/add`, `/participants/slim`                                                                        | `@ownerScopeService.canSeeMission`                                                                                                        |
| **Ein-/Auschecken** beim Einsatz                                                                                                                                                                                           | `POST /api/v1/missions/{id}/participants/{pid}/check-in[/slim]`, `…/check-out[/slim]`                                                      | `@missionSecurityService.canAccessParticipant`                                                                                            |
| **Eigenen Gast-Teilnehmer bearbeiten** (Job-Typ, Schiff, Kommentar, Zeiten)                                                                                                                                                | `PUT /api/v1/missions/{id}/participants/{pid}[/slim]`                                                                                      | `canAccessParticipant`                                                                                                                    |
| **Auszahlungsart ändern** (Auszahlungspräferenz, z. B. `DONATE`)                                                                                                                                                           | `PUT /api/v1/missions/{id}/participants/{pid}/payout-preference[/slim]`                                                                    | `canAccessParticipant`                                                                                                                    |
| **Eigenen Gast-Teilnehmer entfernen**                                                                                                                                                                                      | `DELETE /api/v1/missions/{id}/participants/{pid}[/slim]`                                                                                   | `canAccessParticipant`                                                                                                                    |

**Warum die Teilnehmer-Endpunkte anonym funktionieren:** Ein **Gast-Teilnehmer
ist nicht mit einem Benutzerkonto verknüpft** (`participant.user == null`).
[`MissionSecurityService.canAccessParticipant`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/MissionSecurityService.java)
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
- **Finanz-Einträge eines Einsatzes lesen oder anlegen** — die Finanz-Ledger-Fläche
  (`GET`/`POST /api/v1/.../finance-entries`) ist die Auszahlungssicht des Einsatzes und
  verlangt Mitglied-oder-höher (`isMemberOrAbove`). Anonym → `401`, ein eingeloggter
  **Guest** → `403` (siehe „Anonym ≈ Rolle Guest" unten). Das Anlegen von Finanz-Einträgen
  ist damit **nicht mehr anonym**.
- **Die Beschreibung eines Einsatzes sehen** — die Freitext-Beschreibung wird in der
  öffentlichen Mission-Antwort serverseitig entfernt (§1.3). Organisation, Teilnehmerliste
  (ohne PII), Einheiten, Frequenzen und Auszahlungsart bleiben dagegen sichtbar.
- **Teilnehmer-PII sehen** (E-Mail, Realname) — wird in jeder Outsider-Antwort entfernt;
  sichtbar bleibt nur das öffentliche Callsign (username/displayName/Rang).
- **Material-Claims, Refinery, Hangar, Lager, Persönliches Inventar/Blueprints,
  Benutzerverzeichnis, Promotion-System, Admin-Bereich** — alles angemeldet
  bzw. rollen-gegated.

### 1.3 Datenschutz für Outsider (zwei Redaktionsstufen)

Mission-Antworten werden serverseitig in [`MissionController`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/controller/MissionController.java)
in **zwei Stufen** bereinigt:

- **Mitglied-Peer** (`cleanupMissionForGuest` / `cleanupParticipantForGuest`) — für ein
  eingeloggtes Mitglied unterhalb Logistician: Owner/Manager/interne Lager-/Refinery-Bezüge
  werden geleert und bei Teilnehmern E-Mail, Realname und Rollen entfernt — die Roster-Sicht
  bleibt aber erhalten.
- **Outsider** (`cleanupOutsiderMissionForGuest`) — für **anonyme UND Guest**-Aufrufer
  (`isMemberOrAbove() == false`): wie Mitglied-Peer, **zusätzlich nur die Beschreibung**
  entfernt. Sichtbar bleiben (auf nicht-internen Einsätzen) Organisation (`owningSquadron`),
  Teilnehmerliste (PII-bereinigt) inkl. Auszahlungsart, Einheiten und Frequenzen — plus Name,
  Zeitplan, Status, Kalenderlink, Teilnehmerzähler und Partyleiter. Das Finanz-Ledger
  (`/finance-entries`) ist eine eigene Fläche und bleibt Mitglied-only. Interne und vergangene
  (`COMPLETED`/`CANCELLED`) Missionen sind für Outsider gar nicht sichtbar (`403`).

**Namen, E-Mails und Tokens landen nie in einer Outsider-Antwort.** Die Namenskonvention
`cleanup…ForGuest` wird von der ArchUnit-Regel
`anonymousReadableMissionEndpointsMustRedactGuestPii` strukturell erzwungen.

> **Anonym ≈ Rolle „Guest" bei den Einsätzen.** „Anonym" = gar nicht eingeloggt (kein JWT).
> Die **Rolle `GUEST`** ist ein *angemeldeter* Keycloak-User ganz ohne Authorities (siehe §2).
> Beide sind „Mission-Outsider" (`AuthHelperService.isMemberOrAbove() == false`) und werden
> **auf der Einsatz-Fläche identisch behandelt**: gleiche redigierte Detailsicht (§1.3),
> dieselben Anmelde-/Gast-Teilnehmer-Rechte und **kein** Zugriff auf das Finanz-Ledger
> (anonym `401`, Guest `403`). Außerhalb der Einsätze bleibt der Unterschied bestehen: ein
> Guest passiert `isAuthenticated()`-Gates (sieht also z. B. sein eigenes — leeres —
> Inventar), scheitert aber an jedem `hasRole(...)`/`hasAuthority(...)`-Check; ein anonymer
> Aufrufer erreicht nur die `permitAll`-Liste.

---

## 2. Rollen & Basis-Berechtigungen

Rollen werden aus den Keycloak-Realm-Rollen abgeleitet (`ROLE_<GROSS_SNAKE>`)
und im
[`DataInitializer`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/DataInitializer.java)
mit Authorities geseedet. Zusätzlich gilt eine **Rollen-Hierarchie**.

### Rollen-Hierarchie (backend + frontend identisch)

```
ROLE_ADMIN   > ROLE_LOGISTICIAN
ROLE_OFFICER > ROLE_LOGISTICIAN
ROLE_ADMIN   > ROLE_MISSION_MANAGER
ROLE_OFFICER > ROLE_MISSION_MANAGER
ROLE_ADMIN           > ROLE_BANK_MANAGEMENT
ROLE_BANK_MANAGEMENT > ROLE_BANK_EMPLOYEE
```

Admins und Officer erfüllen damit automatisch jeden `LOGISTICIAN`- und
`MISSION_MANAGER`-Check. Admins erfüllen zusätzlich jeden Bank-Check
(`BANK_MANAGEMENT` und transitiv `BANK_EMPLOYEE`); die Bankleitung erfüllt jeden
`BANK_EMPLOYEE`-Check (REQ-BANK-007).

### Geseedete Authorities

| Rolle               | Authorities (DataInitializer)                                                                                                                                     |
|:--------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Admin**           | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE` (+ `LOGISTICIAN`/`MISSION_MANAGER` via Hierarchie) |
| **Officer**         | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE` (+ `LOGISTICIAN`/`MISSION_MANAGER` via Hierarchie)                |
| **Squadron Member** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`                                                                                                                     |
| **Guest**           | *(keine — leeres Set)*                                                                                                                                            |
| **Bank Employee**   | *(keine — die Feinrechte sind app-verwaltete Grant-Zeilen, REQ-BANK-009)*                                                                                         |
| **Bank Management** | *(keine — Sichtbarkeit "alles" kommt aus der Rolle selbst, ADR-0011)*                                                                                             |

`USER_MANAGE` bleibt aus historischen Gründen im Officer-Set, wird aber von
keinem Endpunkt mehr geprüft (effektiv inert — alle Member-Management-Endpunkte
sind seit dem Phase-4-Lockdown `hasRole('ADMIN')`).

### Kontextuelle Rollen aus OrgUnit-Mitgliedschaften

`LOGISTICIAN` und `MISSION_MANAGER` sind **keine** Keycloak-Rollen, sondern
**Flags pro OrgUnit-Mitgliedschaft** (`org_unit_membership.is_logistician` /
`is_mission_manager`). Der
[`CustomJwtGrantedAuthoritiesConverter`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/config/CustomJwtGrantedAuthoritiesConverter.java)
befördert daraus zwei Authority-Flächen:

- **Flach** `ROLE_LOGISTICIAN` / `ROLE_MISSION_MANAGER`, sobald **irgendeine**
  Mitgliedschaft (Staffel oder SK) das Flag trägt — damit funktionieren alle
  bestehenden `hasRole('LOGISTICIAN')`-Gates.
- **Kontextuell** `ROLE_LOGISTICIAN@<orgUnitId>` pro (Mitgliedschaft, Flag)-Paar
  — damit das per-OrgUnit-Scoping am `@PreAuthorize`-Aufrufort
  (`@ownerScopeService.canEdit…`) ohne Service-Roundtrip aufgelöst werden kann.

> Die **alten** `app_user.is_logistician` / `app_user.is_mission_manager`-Spalten
> wurden mit **V104** entfernt — Quelle der Wahrheit ist ausschließlich
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

| Funktion (Gate)                                                                          | Anonym | Member | Log. | MM | Officer | Admin |
|:-----------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Angemeldet sein (`isAuthenticated()`)                                                    |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Eigenes Profil / `GET /me`, aktiver OrgUnit-Kontext (`/me/active-org-unit`)              |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Benutzerverzeichnis lesen (`/users`, `/search`, `/lookup`, `/{id}`, `/{id}/memberships`) |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |

### 3.2 Hangar & Persönliche Daten

| Funktion (Gate)                                                                                        | Anonym | Member | Log. | MM | Officer | Admin |
|:-------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Hangar lesen (`HANGAR_READ`)                                                                           |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Eigene Schiffe pflegen / Import (CCU, HangarXPLOR, Fleetyards, StarJump) (`isAuthenticated()` + Owner) |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Schiffe anderer Member verwalten (`hasRole('ADMIN')`)                                                  |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| `resetAllFittedStatus` (`hasAnyRole('ADMIN','OFFICER')`)                                               |   ❌    |   ❌    |  ❌   | ❌  |    ✅    |   ✅   |
| Persönliches Inventar / Persönliche Blueprints (eigene) (`isAuthenticated()`)                          |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Persönl. Inventar/Blueprints **anderer** verwalten (`/admin/...`, `hasRole('ADMIN')`)                  |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| Blueprint-Verfügbarkeit der Orgeinheit lesen (`/blueprint-overview`, `canAccessBlueprintOverview`)     |   ❌    |   ❌    |  ❌¹  | ❌  |    ✅    |   ✅   |

¹ SK-Leads sehen die Übersicht zusätzlich **für ihre SK** (über das `is_lead`-Flag, nicht über das reine Logistician-Flag). Officer sehen nur ihre Staffel; Admins ohne Pin alle Orgeinheiten, mit Pin nur die angepinnte.

### 3.3 Lager (Inventory) & Aufträge (Job Orders)

| Funktion (Gate)                                                                                                             | Anonym | Member | Log. | MM  | Officer | Admin |
|:----------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:---:|:-------:|:-----:|
| Lager-View ansehen (`/inventory`, Member+)                                                                                  |   ❌    |   ✅    |  ✅   |  ✅  |    ✅    |   ✅   |
| Lager bearbeiten / aus-/einbuchen (`isAuthenticated()` + `canEditInventoryItem`, Owner-Scope)                               |   ❌    |   ✅¹   |  ✅   | ✅¹  |    ✅    |   ✅   |
| Auftrag **anlegen** (Material- & Item-Auftrag)                                                                              |   ✅    |   ✅    |  ✅   |  ✅  |    ✅    |   ✅   |
| Auftrags-Liste / -Detail lesen (`isAuthenticated()` + `canViewJobOrders` + `canSeeJobOrder`)                                |   ❌    |   ✅³   |  ✅³  | ✅³  |   ✅³    |   ✅   |
| Sich selbst als **Bearbeiter** ein-/austragen, eigene Bearbeiter-Notiz pflegen (`canSeeJobOrder` + selbst-oder-Logistician) |   ❌    |  ✅³⁵   |  ✅³  | ✅³⁵ |   ✅³    |   ✅   |
| **Blaupausen-Abdeckung** eines Item-Auftrags lesen (`canSeeJobOrderBlueprintOwners`)                                        |   ❌    |   ✅⁴   |  ✅⁴  | ✅⁴  |   ✅⁴    |   ✅   |
| Auftrag **bearbeiten** (Status, Priorität, Materialien, Handover) (`hasRole('LOGISTICIAN')` + `canEditJobOrder`)            |   ❌    |   ❌    |  ✅³  |  ❌  |   ✅³    |   ✅   |
| Verantwortliche Einheit umsetzen (`PATCH /{id}/responsible-org-unit`)                                                       |   ❌    |   ❌    |  ✅²  |  ❌  |   ✅²    |   ✅   |
| Material-Claims auf SK-Aufträgen eintragen/zurückziehen (`hasRole('LOGISTICIAN')` + `canViewJobOrders`)                     |   ❌    |   ❌    |  ✅³  |  ❌  |   ✅³    |   ✅   |
| Auftrag **löschen** (`hasRole('ADMIN')`)                                                                                    |   ❌    |   ❌    |  ❌   |  ❌  |    ❌    |   ✅   |

¹ Nur über das eigene Lager / die Owner-Scope-Prüfung — nicht generell.
² Admin frei; Staffel-Logistiker/-Officer nur **Eskalation** des eigenen
Staffel-Auftrags an ein SK.
³ **Nur Mitglieder einer profit-berechtigten Orgeinheit** (`is_profit_eligible`
auf Staffel oder SK) sind Teil des Auftrags-Workflows: nur sie dürfen Aufträge
**sehen** (Liste/Detail), **bearbeiten** (Status/Priorität/Materialien/Handover,
Reassign) und **Material-Claims** setzen/zurückziehen — der Profit-Gate
(`canViewJobOrders`) ist in `canSeeJobOrder` und `canEditJobOrder` eingefaltet und
gilt auch für die rollen-only Claim-Endpunkte. Wer ausschließlich in
nicht-profit-berechtigten Einheiten ist, kann Aufträge **nur anlegen** — sonst
nichts, analog zu anonymen Gästen. Admins haben immer Zugriff (`canViewJobOrders`
ist für sie immer wahr). Im Frontend wird der „Aufträge"-Link durch „Auftrag
anlegen" ersetzt und ein Direktaufruf von `/orders` bzw. `/orders/{id}` auf das
Anlege-Formular umgeleitet; das Backend liefert für Nicht-Profit-Mitglieder eine
leere Liste bzw. `403` (auch bei Schreib-/Claim-Endpunkten).
⁴ Strenger als `canSeeJobOrder` — **kein** SK-Public-Escape: Die Abdeckungssicht
nennt Mitglieder namentlich samt ihrer Blaupausen und ist deshalb auf Mitglieder
der **bearbeitenden** (responsible) Orgeinheit beschränkt. Wer den SK-Auftrag nur
über die öffentliche Warteschlange sieht, bekommt `403`; das Frontend blendet den
Abschnitt aus. Admins ohne Pin immer, mit Pin nur bei passender Einheit.
⁵ Selbst-oder-Logistician-Regel (`verifyAssigneeAccess`): Jeder, der den Auftrag
sieht, darf **sich selbst** ein-/austragen und **seine eigene** Notiz (max. 500
Zeichen) pflegen; fremde Einträge/Notizen darf nur ein Logistician+ ändern.
Notizen sind für alle sichtbar, die den Auftrag sehen.

### 3.4 Refinery

| Funktion (Gate)                                                                                                                        | Anonym | Member | Log. | MM | Officer | Admin |
|:---------------------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Eigene Refinery-Orders lesen/anlegen, inkl. Screenshot-Import (`POST /import-extract`) (`isAuthenticated()` [+ `canSeeRefineryOrder`]) |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Refinery-Order bearbeiten/löschen/lagern (`isAuthenticated()` + `canEditRefineryOrder`: Owner **oder** Logistician)                    |   ❌    |   ✅¹   |  ✅   | ✅¹ |    ✅    |   ✅   |
| Refinery-Orders **für andere** anlegen/verwalten (`/users/{id}`, `hasRole('LOGISTICIAN')`)                                             |   ❌    |   ❌    |  ✅   | ❌  |    ✅    |   ✅   |

¹ Nur als Owner der jeweiligen Order.

### 3.5 Einsätze (Missionen)

| Funktion (Gate)                                                                                              | Anonym | Member | Log. | MM | Officer | Admin |
|:-------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Nicht-interne Missionen lesen (`canSeeMission`; Outsider-redigiert, §1.3)                                    |   ✅⁴   |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Mission **anlegen** (`isAuthenticated()`)                                                                    |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Als (Gast-)Teilnehmer anmelden / ein-/auschecken / Auszahlungsart ändern / abmelden (`canAccessParticipant`) |   ✅¹   |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Mission **verwalten** (bearbeiten, Teilnehmer/Units/Crew/Frequenzen, Party-Lead) (`canManageMission`)        |   ❌    |   ✅²   |  ✅²  | ✅³ |   ✅³    |   ✅   |
| Manager / Owner setzen (`canManageManagers` / `canChangeOwner`)                                              |   ❌    |   ✅²   |  ✅²  | ✅² |   ✅³    |   ✅   |
| Mission **löschen** (`hasRole('ADMIN')`)                                                                     |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |

¹ Anonym nur auf **unverknüpften Gast-Teilnehmern**; eingeloggte User nur auf
ihrem eigenen verknüpften Teilnehmer. ² Nur als **Owner/Co-Manager** der Mission.
³ Mission-Manager/Officer zusätzlich nur im eigenen Staffel-Scope
(`canEditMission`). ⁴ Outsider (anonym **und** rollenloser Guest, `isMemberOrAbove() == false`)
sehen die Detailsicht ohne Beschreibung + PII; Organisation, Teilnehmerliste, Einheiten,
Frequenzen und Auszahlungsart bleiben sichtbar (§1.3). Das Finanz-Ledger bleibt Mitglied-only,
und ein Guest wird hier wie ein anonymer Besucher behandelt.

> **Einsatz ohne Orgeinheit (Bereichsleitung).** „Mission anlegen" ist `isAuthenticated()` — das
> schließt einen angemeldeten Nutzer **ohne** Staffel-/SK-Mitgliedschaft ein (z. B. die den SKs und
> Staffeln übergeordnete Bereichsleitung). Sein Einsatz wird **ownerless** angelegt
> (`owning_org_unit_id = NULL`, V144) statt mit `400` abgelehnt und bleibt über `mission.owner_id`
> zurechenbar. Sichtbarkeit: **nicht intern → für alle sichtbar** (auch anonym); **intern → nur für
> Mitglieder-oder-höher** (`isMemberOrAbove()`), für Gäste/Anonyme unsichtbar. Bearbeiten folgt dem
> üblichen Mission-Management-Gate (Owner, Co-Manager, Mission-Manager/Officer, Admins), ohne
> Staffel-Scope-Einengung. Details: REQ-ORG-009 in
> [`docs/specs/org-unit-tenancy.md`](docs/specs/org-unit-tenancy.md).

### 3.6 Operations (Einsatz-Klammer, Finanzen & Auszahlungen)

| Funktion (Gate)                                                                                   | Anonym | Member | Log. | MM | Officer | Admin |
|:--------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Operations lesen (Liste/Detail/Finanzen/Auszahlungen) (`isAuthenticated()` [+ `canSeeOperation`]) |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Operation anlegen/bearbeiten (`hasRole('MISSION_MANAGER')` [+ `canEditOperation`])                |   ❌    |   ❌    |  ❌   | ✅  |    ✅    |   ✅   |
| Auszahlung als **paid-out markieren** (`hasRole('MISSION_MANAGER')` + `canEditOperation`)         |   ❌    |   ❌    |  ❌   | ✅  |    ✅    |   ✅   |
| paid-out **zurücknehmen** (zusätzlich `hasAnyRole('ADMIN','OFFICER')`)                            |   ❌    |   ❌    |  ❌   | ❌  |    ✅    |   ✅   |
| Operation **löschen** (`hasRole('ADMIN')` + `canEditOperation`)                                   |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |

> Asymmetrie der Auszahlung: jeder Mission-Manager darf `paidOut=true` setzen,
> aber nur Officer/Admin dürfen ein bestätigtes paid-out wieder auf `false`
> zurücksetzen.
>
> **Sichtbarkeit (`canSeeOperation`) hat seit #500/#501 drei Pfade** (es genügt einer):
> **(1)** der normale Staffel-Scope (Operation der eigenen Orgeinheit);
> **(2)** eine **ownerless Bereichsleitungs-Operation** (`owning_org_unit_id = NULL`,
> V145, ADR-0005) ist für **alle Mitglieder-oder-höher** sichtbar — Operations haben
> keinen Public-Escape, für Gäste/Anonyme bleibt sie unsichtbar;
> **(3)** **Teilnehmer-Sichtbarkeit** (ADR-0006): Wer an einem der verknüpften
> Einsätze teilgenommen hat, sieht die Operation und seine Auszahlung auch
> staffelfremd (anonyme Aufrufer nie — kein `currentUserId`).
> Anlegen einer ownerless Operation steht jedem Mission-Manager **ohne**
> Orgeinheit offen (Bereichsleitung); bearbeiten darf sie jeder Mission-Manager,
> löschen jeder Admin (`canEditOperation` ist für ownerless Operationen ein
> No-op, das Rollen-Gate des Endpunkts trägt die Einschränkung).

### 3.7 Mission-Finanzen & Profit

| Funktion (Gate)                                                                         | Anonym | Member | Log. | MM | Officer | Admin |
|:----------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Finanz-Einträge einer Mission lesen (`isMemberOrAbove` + `canSeeMission`)               |   ❌²   |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Finanz-Eintrag **anlegen** (`isMemberOrAbove` + `canSeeMission`)                        |   ❌²   |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Finanz-Eintrag bearbeiten/löschen (`canEditFinanceEntry`: Owner **oder** Officer/Admin) |   ❌    |   ✅¹   |  ✅¹  | ✅¹ |    ✅    |   ✅   |
| Profit-Kalkulation lesen (`hasAnyRole('SQUADRON_MEMBER','MEMBER','OFFICER','ADMIN')`)   |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Material-Übersicht / Material-Collection eines Auftrags (`isAuthenticated()`)           |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |

¹ Nur eigener Eintrag und nur solange weiterhin Teilnehmer der Mission.
² Das Finanz-Ledger ist die Auszahlungssicht und verlangt Mitglied-oder-höher: anonym → `401`,
rollenloser Guest → `403` (Guest wird bei Einsätzen wie anonym behandelt, §1.3). Aufträge anlegen
bleibt davon unberührt (für alle möglich).

### 3.8 Promotion-System (Beförderung)

Alle Promotion-Controller sind class-level `isAuthenticated()`. Das Beförderungssystem
ist **durchgängig staffel-gescopt**: jede Staffel führt ihr eigenes System
(Themenbereiche, Kategorien, Level-Inhalte, Rangvoraussetzungen, Bewertungen) und
sieht ausschließlich die eigenen Daten. Lese- **und** Schreibzugriff werden in der
Service-Schicht über den aktiven Staffel-Kontext gefiltert
(`OwnerScopeService.currentSquadronId()` für Listen/Eligibility,
`canSeeSquadron`/`canEditSquadron(topic.owningSquadron.id)` für Detail und Pflege).

- **Member/Officer** sehen nur das System ihrer Heimat-Staffel.
- **Admins** sehen das der aktiv angepinnten Staffel; ohne Pin (Alle-Staffeln-Modus)
  zeigen die Seiten einen „Staffel wählen"-Hinweis statt einer Vermischung aller Staffeln.
- Ein **Nutzer ohne Staffelzugehörigkeit, der kein Admin ist**, hat kein eigenes
  Beförderungssystem: der Menüpunkt ist ausgeblendet, jeder Listen-/Eligibility-Read
  liefert leer und ein direkter Seitenaufruf wird mit 403 blockiert (`hasPromotionReadAccess()`).

| Funktion (Gate)                                                                                                      | Anonym | Member | Log. | MM | Officer | Admin |
|:---------------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Themenbereiche/Kategorien/Level-Inhalte/Rangvoraussetzungen lesen (`isAuthenticated()`, **nur eigene Staffel**)      |   ❌    |   ✅¹   |  ✅¹  | ✅¹ |   ✅¹    |  ✅²   |
| …**pflegen** (Service: Admin **oder** Officer der besitzenden Staffel)                                               |   ❌    |   ❌    |  ❌   | ❌  |   ✅³    |   ✅   |
| Eigene Bewertungen / Eligibility ansehen (`/my`, JWT-Sub, **eigene Staffel**)                                        |   ❌    |   ✅¹   |  ✅¹  | ✅¹ |   ✅¹    |  ✅²   |
| Bewertungen/Eligibility **anderer** ansehen, Member-Liste (`hasAnyRole('ADMIN','OFFICER')`, Officer staffel-gescopt) |   ❌    |   ❌    |  ❌   | ❌  |    ✅    |   ✅   |
| Promotion-Subsystem je Staffel an-/abschalten (`PATCH /squadrons/{id}/promotion-enabled`, `hasRole('ADMIN')`)        |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |

¹ Nur die **eigene Heimat-Staffel**; ein Nutzer ganz ohne Staffel (und ohne Admin-Rechte) sieht nichts — `hasPromotionReadAccess()` liefert leer, das Menü ist ausgeblendet, Direktaufruf 403.
² Admin: die aktiv angepinnte Staffel; im Alle-Staffeln-Modus ein „Staffel wählen"-Hinweis statt einer Vermischung.
³ Nur für die eigene Staffel. **SKs sind vom Promotion-System per DB-CHECK/Trigger + ArchUnit-Regel dauerhaft ausgeschlossen.**

### 3.9 Organisation (Staffeln & Spezialkommandos)

| Funktion (Gate)                                                                                                                                                              | Anonym | Member | Log. | MM | Officer | Admin |
|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| Staffel-Liste / aktive OrgUnit-Liste (`/org-units/active`) lesen                                                                                                             |   ✅¹   |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| SK-Liste lesen (`isAuthenticated()`; inaktive **und** die Detailansicht `GET /special-commands/{id}` nur Admin)                                                              |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Aktiven OrgUnit-Kontext umschalten (Sidebar-Switcher)                                                                                                                        |   ❌    |   ✅²   |  ✅²  | ✅² |   ✅²    |   ✅   |
| Staffel-Lifecycle (anlegen/umbenennen/löschen/aktivieren, `promotion-enabled`, `profit-eligible`) (`hasRole('ADMIN')`)                                                       |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| Staffel-Mitglieds-Flags setzen (`PATCH /squadrons/{id}/members/{uid}`, `hasRole('ADMIN')`)                                                                                   |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| SK-Lifecycle (anlegen/umbenennen/löschen/aktivieren, `profit-eligible`) (`hasRole('ADMIN')`)                                                                                 |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| SK-**Mitgliederliste lesen** & **Mitglieder verwalten** (add/remove/Flags) (`@specialCommandSecurityService.canManageMembers` — gilt auch für das reine `GET /{id}/members`) |   ❌    |   ❌    |  ❌   | ❌  |   ❌³    |   ✅   |
| SK-**Lead-Flag** setzen (`PATCH /special-commands/{id}/members/{uid}/lead`, `hasRole('ADMIN')`)                                                                              |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |

¹ Stammdaten-Read, anonym erlaubt. ² Nicht-Admins schalten zwischen ihren
Mitgliedschaften; Admins zusätzlich „Alle Staffeln". ³ SK-Mitgliederverwaltung
ist **Admin oder SK-Lead dieses SK** — nicht an die globale Officer-Rolle
gebunden.

### 3.10 Stammdaten, Ankündigungen, System

| Funktion (Gate)                                                                                                                                                         | Anonym | Member | Log. | MM | Officer | Admin |
|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:----:|:--:|:-------:|:-----:|
| **Öffentlich** lesbare Stammdaten (Materialien, Locations, Schiffstypen, Hersteller, Sternensysteme, Refining-Methoden, Frequenz-/Job-Typen, Staffeln, System-Settings) |   ✅    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| **Angemeldet** lesbare Stammdaten (Terminals, Material-Kategorien)                                                                                                      |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| **Admin-only** Stammdaten – auch zum Lesen (Städte, Raumstationen, Outposts, POIs, Material-Aliase, Blueprints) (`hasRole('ADMIN')`)                                    |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| Stammdaten **schreiben** (anlegen/ändern/löschen/Sichtbarkeit/Overrides) (`hasRole('ADMIN')`)                                                                           |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| UEX-Location-Typeahead / Blueprint-Produkt-Suche (`isAuthenticated()`)                                                                                                  |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Ankündigung **lesen** (`GET /announcement`, `isAuthenticated()`)                                                                                                        |   ❌    |   ✅    |  ✅   | ✅  |    ✅    |   ✅   |
| Ankündigung **schreiben/löschen** (inkl. Roh-Lesesicht `GET /announcement/admin`) (`hasRole('ADMIN')`)                                                                  |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| Sync-Reports lesen/aufräumen (`hasRole('ADMIN')`)                                                                                                                       |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| System-Setting schreiben (`PUT /settings/{key}`, `hasRole('ADMIN')`)                                                                                                    |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |
| Rollen-/Rechteverwaltung, Member-Attribute/Rang, Flag-Vergabe (`/admin/**`, `/users/*/...`, `hasRole('ADMIN')`)                                                         |   ❌    |   ❌    |  ❌   | ❌  |    ❌    |   ✅   |

Welche Stammdaten anonym lesbar sind, legt allein die `permitAll`-Liste in
`SecurityConfig` fest (siehe §1.1) — alles andere ist mindestens angemeldet,
einige Tabellen (Städte, Stationen, Outposts, POIs, Aliase, Blueprints) sind
schon zum **Lesen** Admin-only. **Schreiben ist bei allen Stammdaten
Admin-only.**

### 3.11 Kartellbank (epic #556)

Die Bank hängt an zwei eigenen Keycloak-Rollen (`Bank Employee` →
`ROLE_BANK_EMPLOYEE`, `Bank Management` → `ROLE_BANK_MANAGEMENT`) plus
app-verwalteten **Grant-Zeilen** pro (Mitarbeiter, Konto)
(`bank_account_grant`: Zeile = Lese-Recht; Flags = einzahlen / auszahlen /
transferieren). **Bankmitarbeit ist völlig unabhängig von
OrgUnit-Mitgliedschaften** (REQ-BANK-008): `BankSecurityService` wertet
ausschließlich Bankrollen + Grants aus — `OwnerScopeService`, kontextuelle
Rollen und der Admin-Pin haben keinerlei Einfluss, in beide Richtungen.
Spalten hier: **Member** = beliebige Org-Rolle ohne Bankrolle · **Bank-MA** =
`Bank Employee` (mit Grants) · **Bankleitung** = `Bank Management`.

| Funktion (Gate)                                                                                        | Anonym | Member |  Bank-MA  | Bankleitung | Admin |
|:-------------------------------------------------------------------------------------------------------|:------:|:------:|:---------:|:-----------:|:-----:|
| Bankbereich betreten, Dashboard, Konten **mit Grant-Zeile** sehen (`hasRole('BANK_EMPLOYEE')` + Grant) |   ❌    |   ❌    |     ✅     |      ✅      |   ✅   |
| **Alle** Konten/Halter/Grants sehen (`hasRole('BANK_MANAGEMENT')`)                                     |   ❌    |   ❌    |     ❌     |      ✅      |   ✅   |
| Einzahlen / Auszahlen / Transfer (`@bankSecurityService.canDeposit/Withdraw/Transfer`, je Konto-Flag)  |   ❌    |   ❌    | ✅ je Flag |      ✅      |   ✅   |
| Konten anlegen/umbenennen/schließen/wiedereröffnen, Halter-Registry, Grants verwalten                  |   ❌    |   ❌    |     ❌     |      ✅      |   ✅   |
| Storno (`POST /bank/transactions/{id}/reversal`, `hasRole('BANK_MANAGEMENT')`)                         |   ❌    |   ❌    |     ❌     |      ✅      |   ✅   |
| Kontoauszug-PDF (gesehene Konten) / 3-Monats-Report (`BANK_MANAGEMENT`)                                |   ❌    |   ❌    |   ✅ / ❌   |      ✅      |   ✅   |
| **Audit-Log** lesen (`/api/v1/bank/admin/audit`, URL- **und** Methoden-Gate `hasRole('ADMIN')`)        |   ❌    |   ❌    |     ❌     |      ❌      |   ✅   |
| **Wipe-Reset** (`/api/v1/bank/admin/wipe-reset`, `hasRole('ADMIN')`)                                   |   ❌    |   ❌    |     ❌     |      ❌      |   ✅   |

Die Bankleitung sieht das Audit-Log **nicht** — es ist bewusst Admin-only
(REQ-BANK-012). Grants können nur an Nutzer mit der Rolle `Bank Employee`
vergeben werden (409 `BANK_GRANTEE_MISSING_ROLE`); ob die Person zusätzlich in
einer Staffel/einem SK ist, spielt keine Rolle.

#### 3.11.1 Org-Einheits-Zugang für Offiziere/Leads (epic #666)

Epic #666 ergänzt **eine einzige, eng umrissene** Org-Einheits-Fähigkeit, **ohne**
die Unabhängigkeit aus REQ-BANK-008 aufzuweichen: `BankSecurityService` und das
Hauptbuch bleiben zu 100 % OrgUnit-blind. Die gesamte OrgUnit-Logik liegt in genau
einem bewusst **nicht** mit `Bank` benannten Seam, `OrgUnitBankAccessService`
(ADR-0020), der als einziger `OwnerScopeService` und Bank verbinden darf
(ArchUnit-pinned). Diese Oberfläche liegt **außerhalb** des Bank-URL-/Rollenraums
unter `/api/v1/org-units/bank/**` und braucht **keine** Bankrolle. „Off./Lead" =
Offizier der eigenen Staffel bzw. Lead des eigenen Spezialkommandos (Aufsichtsscope
`currentBlueprintOversightScope()`); ein einfacher Member sieht weiterhin nichts.

| Funktion (Gate)                                                                                                          | Anonym | Member | Off./Lead |  Bank-MA  | Bankleitung | Admin |
|:-------------------------------------------------------------------------------------------------------------------------|:------:|:------:|:---------:|:---------:|:-----------:|:-----:|
| **Nur Kontostand** des eigenen OrgUnit-Kontos sehen (`GET /api/v1/org-units/bank/balances`, Aufsichtsscope)              |   ❌    |   ❌    |     ✅     |   (✅)*    |    (✅)*     |   ✅   |
| Ein-/Auszahlungs**antrag** anlegen / eigene Anträge sehen / eigenen Antrag zurückziehen (`/org-units/bank/requests/**`)  |   ❌    |   ❌    |     ✅     |   (✅)*    |    (✅)*     |   ✅   |
| Antrag **bestätigen** (bucht, Halter erfassen) / **ablehnen** (`/api/v1/bank/requests/**`, `BANK_EMPLOYEE` + Konto-Flag) |   ❌    |   ❌    |     ❌     | ✅ je Flag |      ✅      |   ✅   |

\* Bank-MA/Bankleitung erreichen die Offiziers-Endpunkte nur, soweit sie selbst der
Aufsichtsscope abdeckt (sie sind als Bankpersonal nicht automatisch Offizier/Lead) —
ihre eigentliche Bankarbeit läuft über den Bankbereich oben. Der Antrag bewegt erst
bei der Bestätigung Geld; Überziehungs-/Halterprüfung greifen dann wie bei einer
direkten Buchung (REQ-BANK-023). Beim Anlegen werden Bankleitung + die für das Konto
berechtigten Bank-MA per In-App-Benachrichtigung informiert (REQ-BANK-026,
`ACCOUNT_GRANT`-Selektor). Ein Konto mit offenem Antrag lässt sich nicht schließen
(409 `BANK_ACCOUNT_HAS_PENDING_REQUESTS`). Das Audit-Log bleibt Admin-only.

---

## 4. Mehr-OrgUnit-Sichtbarkeit (Scoping)

Lese- und Schreibpfade werden über
[`OwnerScopeService`](backend/src/main/java/de/greluc/krt/profit/basetool/backend/service/OwnerScopeService.java)
gefiltert (früher `SquadronScopeService`; deckt heute Staffeln **und**
Spezialkommandos ab). Grundregel: Nicht-Admins sehen die Vereinigung ihrer
Mitgliedschaften; Admins ohne aktiven Pin sehen alles, mit Pin dieselbe
restriktive Sicht wie ein Member.

- **Strikt staffel-gescopt** (kein Cross-Staffel): `Ship`, `InventoryItem`
  (Lager-View), `RefineryOrder` — Listen filtern auf
  `owning_org_unit_id`, Detail-/Schreib-Endpunkte gaten auf
  `canSee*`/`canEdit*`. Persönliche Zeilen ganz ohne Orgeinheit
  (`owning_org_unit_id = NULL`, V132 — z. B. das Schiff eines Nutzers ohne
  Staffel/SK) sind **owner-only**: sichtbar/editierbar nur für den Besitzer
  selbst und Admins (`canAccessOwnerlessPersonalRow`).
- **Cross-Staffel mit Public-Escape**: `Mission` — für andere OrgUnits sichtbar
  genau dann, wenn `is_internal = false`; editierbar nur durch die besitzende
  OrgUnit + Admins. Ownerless Bereichsleitungs-Missionen (V144) folgen
  REQ-ORG-009 (siehe Kasten in §3.5).
- **Staffel-gescopt mit zwei Escapes**: `Operation` — kein Public-Escape, aber
  (a) **ownerless Bereichsleitungs-Operationen** (V145) sind für alle
  Mitglieder-oder-höher sichtbar und (b) **Teilnehmer** der verknüpften
  Einsätze sehen die Operation staffelfremd (#500; Details im Kasten in §3.6).
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
   `app_user`-Flag-Spalten existieren seit V104 nicht mehr.
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

