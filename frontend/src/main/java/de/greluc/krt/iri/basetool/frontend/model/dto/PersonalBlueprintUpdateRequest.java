package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;

/**
 * Outbound write DTO mirroring the backend {@code PersonalBlueprintUpdateRequest} (#327): the
 * editable fields of an owned blueprint plus the optimistic-lock version. Validation is
 * authoritative on the backend.
 *
 * @param acquiredAt in-game acquisition time, or {@code null}
 * @param note free-form note, or {@code null}
 * @param version the last seen optimistic-lock version
 */
public record PersonalBlueprintUpdateRequest(Instant acquiredAt, String note, Long version) {}
