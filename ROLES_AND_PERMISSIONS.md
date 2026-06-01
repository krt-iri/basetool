# Rollen- und Rechte-Matrix (Profit Basetool)

> **Stand 2026-05-23 (Spezialkommando-Erweiterung R6.e abgeschlossen):**
> Die untenstehende Matrix wurde gegen alle `@PreAuthorize`-Annotationen
> in den Backend-Controllern verifiziert. Mit dem Phase-4-Lockdown des
> Multi-Squadron-Umbaus (siehe [`MULTI_SQUADRON_PLAN.md`](MULTI_SQUADRON_PLAN.md))
> wurde der Admin-Bereich auf `hasRole('ADMIN')` verengt; mit der
> Spezialkommando-Erweiterung (siehe `SPEZIALKOMMANDO_PLAN.md`) sind die
> Authority-Quellen fuer `LOGISTICIAN` und `MISSION_MANAGER` von der
> globalen `app_user`-Spalte auf die per-OrgUnit-Membership-Spalte
> umgezogen — der JWT-Konverter befoerdert die flache Rolle, wenn die
> Flag auf *irgendeiner* Staffel-/SK-Mitgliedschaft `true` ist; das
> per-OrgUnit-Scoping erfolgt separat ueber `@PreAuthorize`-Gates an den
> `OwnerScopeService`. SK-spezifisch kommt die `Lead`-Rolle hinzu — ein
> User mit `is_lead = true` auf einer SK-Mitgliedschaft darf in *diesem
> einen* SK Mitglieder hinzufuegen / entfernen / Flags toggeln (ueber den
> `SpecialCommandSecurityService.canManageMembers`-Gate), nichts mehr.
> Officer behalten ihre squadron-internen Funktionen — Mission-Management,
> Hangar-Schreibrechte (inklusive `resetAllFittedStatus`),
> Refinery-Management und den (bedingt staffel-gescopten) Job-Order-Workflow.
> Die `USER_MANAGE`-Authority bleibt aus historischen Gruenden Teil des
> Officer-Rollensatzes in
> [`DataInitializer`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/config/DataInitializer.java),
> wird aber von keinem Endpunkt mehr gepruft (effektiv inert). Falls die
> Matrix von der Implementierung abweicht, zaehlen weiterhin die
> `@PreAuthorize`-Annotationen im Backend.

Dieses Dokument fasst die aktuelle Rollen- und Rechtekonfiguration des Profit Basetools zusammen, basierend auf der Implementierung in Backend-Controllern und der Datenbank-Initialisierung.

Das System nutzt eine Kombination aus **Rollen** (abgeleitet vom Keycloak JWT und in der Datenbank synchronisiert) und zugehörigen **Berechtigungen (Permissions / Authorities)**.

## 1. Definierte Rollen & ihre grundlegenden Berechtigungen

Die Initialisierung der Rollen erfolgt über den `DataInitializer` und weist den Rollen folgende Basis-Berechtigungen (Authorities) zu. Zusätzlich gibt es eine **Rollen-Hierarchie** für die Lagerverwaltung.

### Rollen-Hierarchie
`ROLE_ADMIN > ROLE_LOGISTICIAN`
`ROLE_OFFICER > ROLE_LOGISTICIAN`
`ROLE_ADMIN > ROLE_MISSION_MANAGER`
`ROLE_OFFICER > ROLE_MISSION_MANAGER`

Das bedeutet, dass Administratoren und Offiziere automatisch alle Rechte der Rollen `LOGISTICIAN` und `MISSION_MANAGER` besitzen.

| Rolle | Zugewiesene Berechtigungen (Authorities) |
| :--- | :--- |
| **Admin** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE`, `ROLE_MANAGE`, `ROLE_LOGISTICIAN` (via Hierarchie) |
| **Officer** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ`, `MISSION_WRITE`, `MISSION_MANAGE`, `USER_MANAGE`, `ROLE_LOGISTICIAN` (via Hierarchie) |
| **Logistician** | *(Lager- und Auftragsverwaltung — pro OrgUnit-Mitgliedschaft via `org_unit_membership.is_logistician`; JWT-Konverter befoerdert die flache Rolle bei `true` auf irgendeiner Mitgliedschaft)* |
| **Mission Manager** | *(Globale Missionsverwaltung — pro OrgUnit-Mitgliedschaft via `org_unit_membership.is_mission_manager`; gleiche JWT-Befoerderungslogik wie Logistician)* |
| **SK Lead** | *(Per-SK-Mitgliederverwaltung — `org_unit_membership.is_lead = true` auf einer Spezialkommando-Zeile; gilt nur in diesem einen SK, kein cross-SK-Carry-over)* |
| **Squadron Member** | `HANGAR_READ`, `HANGAR_WRITE`, `MISSION_READ` |
| **Guest** | *(Keine spezifischen Berechtigungen)* |

---

## 2. Matrix: Endpunkte & Funktionsbereiche (Zugriffskontrolle)

Anhand der `@PreAuthorize`-Annotationen in den Controllern ergibt sich folgende Matrix für die verschiedenen Module des Basetools:

| Funktionsbereich / Endpunkt | Guest | Squadron Member | Logistician | Officer | Admin |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Allgemeiner Login (Authenticated)** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Hangar: Lesen** (`HANGAR_READ`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Hangar: Schreiben / Admin-Aktionen** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **User Management** (Roll-Flags, Rank, Attribute) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **User List / Details (Read)** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Refinery Orders (Manage All)** (`hasRole('LOGISTICIAN')`) | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Refinery Orders (Edit / Delete / Store)** (`hasRole('LOGISTICIAN')` or Owner) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Refinery Orders (Read All)** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Lagerverwaltung: Ansehen** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Lagerverwaltung: Bearbeiten** (`hasRole('LOGISTICIAN')`) | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Warenaufträge (Job Orders): Lesen / Erstellen** (`isAuthenticated()`) | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Warenaufträge (Job Orders): Bearbeiten** (`hasRole('LOGISTICIAN')`) | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Warenaufträge (Job Orders): Löschen** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Job Orders: Verantwortliche Einheit umschreiben** (`hasRole('LOGISTICIAN')`; Admin frei, Staffel-Logistiker nur Eskalation Staffel→SK) | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Material-Eintragungen auf SK-Aufträgen: Eintragen / Ändern / Zurückziehen** (`hasRole('LOGISTICIAN')` + eigene Staffel via `canEditOrgUnit` / verantwortliches SK / Admin) | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Missionen: Erstellen** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Missionen: Verwalten (Alle)** (`hasRole('MISSION_MANAGER')`) | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Missionen: Verwalten (Eigene/Delegiert)** (`canManageMission`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Announcements (Ankündigungen) – Schreiben** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Announcements (Ankündigungen) – Lesen** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Schiffsdaten (Ship Types, Manufacturers)** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Basisdatenbank** (Locations, Materials, StarSystems, Terminals, etc.) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Promotion-System (Verwaltung von Kategorien/Themen/Voraussetzungen)** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Member Evaluations: Eigene Bewertungen ansehen** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Member Evaluations: Pflegen (Alle / Upsert / Delete)** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Admin-Dashboard & Settings** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Aktive Staffel umschalten (Switcher)** | ❌ | ❌ | ❌ | ❌ | ✅ |

*\*Hinweis: Bei Missionen gibt es spezifische Checks (z.B. `#userId.toString() == authentication.name`), die es erlauben, dass Benutzer ihre eigenen Zuweisungen verwalten, während Officers/Admins Vollzugriff auf alle Missionen haben.*

## 3. Besonderheiten der Implementierung

1. **Fallback bei Keycloak-Synchronisation:** Wenn JWT-Claims (wie `realm_access.roles`) nicht vollständig in die lokale Datenbank synchronisiert werden können, fällt das System auf die reinen Rollen-Namen aus dem JWT-Token zurück (`ROLE_ADMIN`, `ROLE_OFFICER`, etc.), fügt diesen das Präfix `ROLE_` hinzu und übersetzt sie in Großbuchstaben.
2. **Ränge (Ranks):** Die `UserService`-Logik gibt vor, dass `OFFICER` nur die Ränge 1–12 und `SQUADRON_MEMBER` die Ränge 13–20 erhalten dürfen.
3. **Default Role:** Wird bei der Anmeldung keine bekannte Rolle aus Keycloak übermittelt oder dem User noch keine spezifische Rolle zugewiesen, erhält der Benutzer standardmäßig die Rolle **Guest**.
4. **Logistiker-Flag:** Die Rolle `LOGISTICIAN` kann über das `is_logistician`-Flag in der `users`-Tabelle manuell vergeben werden, unabhängig von Keycloak-Rollen. Seit dem Phase-4-Lockdown des Multi-Squadron-Umbaus ausschließlich durch **Admins** (Officer haben keinen Zugriff mehr auf die Flag-Endpunkte; siehe `@PreAuthorize("hasRole('ADMIN')")` auf `UserController#patchLogistician`).
5. **Missions-Manager-Flag:** Die Rolle `MISSION_MANAGER` kann über das `is_mission_manager`-Flag in der `users`-Tabelle manuell durch **Admins** vergeben werden, analog zur Logistiker-Rolle. Officer haben seit Phase 4 keinen Zugriff mehr (`UserController#patchMissionManager` ist `hasRole('ADMIN')`).
6. **Multi-Squadron-Sichtbarkeit:** Lesepfade werden ueber [`SquadronScopeService`](backend/src/main/java/de/greluc/krt/iri/basetool/backend/service/SquadronScopeService.java) gefiltert. Nicht-Admins sehen ausschliesslich Daten ihrer eigenen Staffel; oeffentliche Missionen (`is_internal = false`) sind zusaetzlich cross-staffel sichtbar. Admins koennen ueber den Sidebar-Switcher (`PUT /api/v1/me/active-squadron`) den aktiven Kontext umschalten oder die "Alle Staffeln"-Sicht anwaehlen. Job-Orders sind seit dem Auftrags-Umbau (#340) **bedingt staffel-gescopt**: Ein von einem Spezialkommando bearbeiteter Auftrag (`responsible_org_unit_id` = SK) ist fuer alle Staffeln sichtbar (gemeinsame Warteschlange, auf die sich Staffeln per Material-Eintragung melden koennen), ein von einer Staffel bearbeiteter Auftrag nur fuer diese Staffel und Admins. Der Auftraggeber (`requesting_org_unit_id`) gewaehrt keine Sichtbarkeit. Die alte `creating_squadron_id`-Spalte entfaellt.
