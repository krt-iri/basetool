package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request DTO for assigning (or clearing) a mission's party lead (Partyleiter) via {@code PUT
 * /api/v1/missions/{id}/party-lead}.
 *
 * <p>Mirrors the participant-add input contract: callers either submit an explicit {@code userId}
 * (picked from the user autocomplete) or a free-text {@code guestName}. A non-blank {@code
 * guestName} that has no {@code userId} is resolved case-insensitively against registered members
 * in the controller — a unique match is linked as a registered party lead, no match is stored as a
 * guest handle, multiple matches return 409. Submitting neither clears the party lead.
 *
 * <p>The {@code version} must match the mission's current {@code partyLeadVersion} (a
 * section-scoped counter, NOT the global {@code Mission.version}) so concurrent party-lead edits
 * surface as a 409 instead of silently overwriting each other. The {@code @Size} cap on {@code
 * guestName} matches {@code Mission.partyLeadGuestName}'s column length.
 *
 * @param userId explicit registered-user reference (from the autocomplete pick), or {@code null}
 * @param guestName free-text party-lead handle, or {@code null}; capped at 100 characters
 * @param version expected {@code partyLeadVersion} of the mission for optimistic-lock validation
 */
public record SetPartyLeadRequest(
    UUID userId, @Size(max = 100) String guestName, @NotNull Long version) {}
