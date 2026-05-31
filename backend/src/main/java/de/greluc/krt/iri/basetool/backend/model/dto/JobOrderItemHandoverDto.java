package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A persisted item-handover event with its delivered lines. Mirrors {@link JobOrderHandoverDto}
 * (the material counterpart) including the cross-staffel audit snapshot — {@code executingUser}
 * plus {@code executingSquadron} record who carried out the handover, since a logistician from one
 * squadron may fulfil another squadron's order.
 *
 * @param id the handover primary key
 * @param jobOrderId the parent order id
 * @param handoverTime when the handover occurred (UTC)
 * @param recipientHandle the recipient's handle
 * @param executingUser slim reference to the user who executed the handover ({@code null} for
 *     pre-audit rows)
 * @param executingSquadron snapshot of that user's squadron at handover time ({@code null} when
 *     unassigned)
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
