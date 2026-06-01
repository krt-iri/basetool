package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Form-binding object for Participant input. {@code orgUnitIds} carries the multi-select org-unit
 * picker used only for guest entries; for a registered participant the affiliations are derived
 * server-side and the list is left empty.
 */
public record ParticipantForm(
    UUID userId,
    @Size(max = 255) String guestName,
    UUID desiredJobTypeId,
    UUID plannedMissionJobTypeId,
    @Size(max = 1000) String comment,
    List<UUID> orgUnitIds,
    String startTime,
    String endTime,
    de.greluc.krt.iri.basetool.frontend.model.PayoutPreference payoutPreference,
    Long version) {}
