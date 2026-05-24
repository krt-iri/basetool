-- Stufe-2-Backstop fuer die Multi-User-Anmeldung am Einsatz (siehe CHANGELOG
-- und MissionParticipantConcurrencyTest). Der In-Memory-Duplikat-Check in
-- MissionService.addParticipant deckt den Normalfall ab, hat aber einen
-- klassischen TOCTOU-Race: zwei parallele "Anmelden"-Klicks desselben Nutzers
-- (Doppelklick, zwei Tabs) sehen beide einen "User noch nicht da"-Snapshot und
-- fuegen beide eine Teilnehmerzeile ein. Ergebnis: eine Geisterzeile in der
-- Roster-Ansicht.
--
-- Dieser Index ist der Datenbank-seitige Backstop: ein partieller UNIQUE-Index
-- auf (mission_id, user_id) wo user_id NOT NULL. Partial, weil Gast-Teilnehmer
-- (user_id IS NULL) bewusst dubliziert sein duerfen — mehrere Gaeste mit
-- demselben Vornamen sind legitim, der In-Memory-Check bleibt fuer Gaeste
-- Best-Effort.
--
-- Backfill zuerst: falls heute schon Dubletten existieren (Pre-Policy-Daten),
-- wird die aelteste Zeile pro (mission_id, user_id)-Paar behalten und der Rest
-- geloescht. Check-In-/Auszahlungs-Daten auf der Originalzeile bleiben damit
-- erhalten. mission_crew muss vor mission_participant geleert werden, weil die
-- FK-Constraint keinen ON DELETE CASCADE traegt (V1-Schema);
-- mission_finance_entry haengt schon mit ON DELETE CASCADE dran (V41).

CREATE TEMP TABLE _v96_dup_participants ON COMMIT DROP AS
SELECT id
FROM (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY mission_id, user_id
               ORDER BY created_at NULLS LAST, id
           ) AS rn
    FROM mission_participant
    WHERE user_id IS NOT NULL
) ranked
WHERE rn > 1;

DELETE FROM mission_crew
WHERE mission_participant_id IN (SELECT id FROM _v96_dup_participants);

DELETE FROM mission_participant
WHERE id IN (SELECT id FROM _v96_dup_participants);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mission_participant_user
    ON mission_participant (mission_id, user_id)
    WHERE user_id IS NOT NULL;
