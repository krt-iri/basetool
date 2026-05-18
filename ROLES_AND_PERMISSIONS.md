# Rollen- und Rechte-Matrix (IRIDIUM Basetool)

> **WICHTIG (Stand 2026-05-18):** Mit dem Multi-Squadron-Umbau (siehe [`MULTI_SQUADRON_PLAN.md`](MULTI_SQUADRON_PLAN.md) und CHANGELOG-Eintrag "Multi-Squadron-Umbau") aendert sich die Officer-Rolle substanziell. Officer verliert den Admin-Bereich (Stammdaten, Member-Management, Announcements, UEX, System-Settings, Promotion-System-Pflege) und behaelt nur noch squadron-interne Funktionen (Mission-Management, Hangar-Schreibrechte inklusive `resetAllFittedStatus`, Refinery-Management, Logistician-Funktionen via Rollen-Hierarchie, JobOrder cross-Staffel). Die Tabelle unten reflektiert die Implementierung **nach** dem Phase-4-Lockdown; einzelne Zellen, die fruehe (vor 2026-05-18) Officer-Zugriff zeigten, sind ggf. noch nicht durchgaengig nachgezogen — bei Diskrepanz zaehlen die `@PreAuthorize`-Annotationen in den Backend-Controllern. Vollstaendige Aktualisierung der Matrix laeuft als Phase-6-Followup.

Dieses Dokument fasst die aktuelle Rollen- und Rechtekonfiguration des IRIDIUM Basetools zusammen, basierend auf der Implementierung in Backend-Controllern und der Datenbank-Initialisierung.

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
| **Logistician** | *(Spezifische Berechtigung für Lager- und Auftragsverwaltung)* |
| **Mission Manager** | *(Spezifische Berechtigung zur globalen Missionsverwaltung)* |
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
| **Missionen: Erstellen** (`isAuthenticated()`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Missionen: Verwalten (Alle)** (`hasRole('MISSION_MANAGER')`) | ❌ | ❌ | ❌ | ✅ | ✅ |
| **Missionen: Verwalten (Eigene/Delegiert)** (`canManageMission`) | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Announcements (Ankündigungen) – Schreiben** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Announcements (Ankündigungen) – Lesen** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Schiffsdaten (Ship Types, Manufacturers)** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Basisdatenbank** (Locations, Materials, StarSystems, Terminals, etc.) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Promotion-System (Verwaltung von Kategorien/Themen/Voraussetzungen)** | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Admin-Dashboard & Settings** (`hasRole('ADMIN')`) | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Aktive Staffel umschalten (Switcher)** | ❌ | ❌ | ❌ | ❌ | ✅ |

*\*Hinweis: Bei Missionen gibt es spezifische Checks (z.B. `#userId.toString() == authentication.name`), die es erlauben, dass Benutzer ihre eigenen Zuweisungen verwalten, während Officers/Admins Vollzugriff auf alle Missionen haben.*

## 3. Besonderheiten der Implementierung

1. **Fallback bei Keycloak-Synchronisation:** Wenn JWT-Claims (wie `realm_access.roles`) nicht vollständig in die lokale Datenbank synchronisiert werden können, fällt das System auf die reinen Rollen-Namen aus dem JWT-Token zurück (`ROLE_ADMIN`, `ROLE_OFFICER`, etc.), fügt diesen das Präfix `ROLE_` hinzu und übersetzt sie in Großbuchstaben.
2. **Ränge (Ranks):** Die `UserService`-Logik gibt vor, dass `OFFICER` nur die Ränge 1–12 und `SQUADRON_MEMBER` die Ränge 13–20 erhalten dürfen.
3. **Default Role:** Wird bei der Anmeldung keine bekannte Rolle aus Keycloak übermittelt oder dem User noch keine spezifische Rolle zugewiesen, erhält der Benutzer standardmäßig die Rolle **Guest**.
4. **Logistiker-Flag:** Die Rolle `LOGISTICIAN` kann über das `is_logistician`-Flag in der `users`-Tabelle manuell durch Admins/Offiziere vergeben werden, unabhängig von Keycloak-Rollen.
5. **Missions-Manager-Flag:** Die Rolle `MISSION_MANAGER` kann über das `is_mission_manager`-Flag in der `users`-Tabelle manuell durch Admins/Offiziere vergeben werden, analog zur Logistiker-Rolle.
