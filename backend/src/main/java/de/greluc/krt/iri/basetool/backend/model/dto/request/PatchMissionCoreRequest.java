package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO fuer ein partielles Update der Stammdaten (Core-Section) eines Einsatzes.
 *
 * <p>Dieses DTO erlaubt es mehreren Nutzern gleichzeitig an verschiedenen Sektionen der
 * Einsatz-Detailseite zu arbeiten, ohne dass Aenderungen an anderen Sektionen (z.B. Teilnehmer,
 * Zeitplan, Ort) zum Verlust der Eingaben in dieser Section fuehren. Optimistisches Locking
 * erfolgt ausschliesslich ueber das {@code version}-Feld der Parent-{@code Mission}; Sub-
 * Collections sind mittels {@code @OptimisticLock(excluded = true)} entkoppelt.
 */
public record PatchMissionCoreRequest(
        @NotBlank @Size(max = 255) String name,
        @Nullable @Size(max = 10000) String description,
        @Nullable @Size(max = 2048) String calendarLink,
        @Nullable @Size(max = 64) String status,
        @NotNull Long version
) {
}
