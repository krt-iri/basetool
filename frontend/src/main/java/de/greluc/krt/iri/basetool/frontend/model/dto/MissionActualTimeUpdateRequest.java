package de.greluc.krt.iri.basetool.frontend.model.dto;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Request DTO für das Setzen der tatsächlichen Start-/Endzeit eines Einsatzes
 * via der "Jetzt"-Buttons in den Einsatz-Details.
 *
 * <p>Das Feld {@code version} ist zwingend erforderlich, damit Spring Data JPAs
 * Optimistic Locking ({@code ObjectOptimisticLockingFailureException}) bei parallelen
 * Änderungen greift und der Endpoint mit HTTP 409 antworten kann. Die Validierung
 * (erlaubte Werte für {@code field}, Präsenz von {@code version}) wird im Controller
 * durchgeführt, um konsistent HTTP 400 zurückzugeben.</p>
 *
 * @param field   Name des zu aktualisierenden Feldes ({@code actualStartTime} oder {@code actualEndTime}).
 * @param value   Zu setzender UTC-Zeitpunkt ({@link Instant}). {@code null} entfernt den Wert.
 * @param version Aktuelle Entity-Version (Optimistic Locking).
 */
public record MissionActualTimeUpdateRequest(
        @Nullable String field,
        @Nullable Instant value,
        @Nullable Long version
) {
}
