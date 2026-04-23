package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO fuer ein partielles Update des Zeitplans (Schedule-Section) eines Einsatzes.
 *
 * <p>Alle Zeitstempel werden in UTC ({@link Instant}) entgegengenommen und gespeichert. Die
 * Anzeige im Frontend erfolgt in der lokalen Zeitzone des Nutzers.
 *
 * <p>Das optimistische Locking erfolgt ueber die Parent-Version der {@code Mission}; parallele
 * Aenderungen an Sub-Collections (Teilnehmer, Units, Finanzen) erhoehen die Parent-Version
 * dank {@code @OptimisticLock(excluded = true)} nicht mehr.
 */
public record PatchMissionScheduleRequest(
        @Nullable Instant meetingTime,
        @Nullable Instant plannedStartTime,
        @Nullable Instant plannedEndTime,
        @Nullable Instant actualStartTime,
        @Nullable Instant actualEndTime,
        @NotNull Long version
) {
}
