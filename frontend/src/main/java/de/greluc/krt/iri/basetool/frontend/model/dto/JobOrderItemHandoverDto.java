package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend {@code JobOrderItemHandoverDto}: a persisted item-handover event
 * with its delivered lines and the executing-user/squadron audit snapshot.
 *
 * @param id the handover id
 * @param jobOrderId the parent order id
 * @param handoverTime when the handover occurred (UTC)
 * @param recipientHandle the recipient's handle
 * @param executingUser slim reference to the executing user, or {@code null}
 * @param executingSquadron snapshot of that user's squadron, or {@code null}
 * @param entries the delivered item-line quantities
 * @param version optimistic-lock version
 */
public record JobOrderItemHandoverDto(
    UUID id,
    UUID jobOrderId,
    Instant handoverTime,
    String recipientHandle,
    @Nullable UserReferenceDto executingUser,
    @Nullable SquadronReferenceDto executingSquadron,
    List<JobOrderItemHandoverEntryDto> entries,
    Long version) {}
