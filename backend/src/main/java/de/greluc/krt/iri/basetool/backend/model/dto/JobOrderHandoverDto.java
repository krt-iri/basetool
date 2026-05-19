package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Job Order Handover payload.
 *
 * <p>Audit fields (MULTI_SQUADRON_PLAN.md section 4.4):
 *
 * <ul>
 *   <li>{@code executingUser} — slim reference to the user who executed the handover, captured at
 *       handover time. {@code null} for historical rows that pre-date the audit columns.
 *   <li>{@code executingSquadron} — snapshot of that user's squadron at handover time. May differ
 *       from the order's requesting/creating squadron (cross-staffel workspace).
 * </ul>
 */
public record JobOrderHandoverDto(
    UUID id,
    UUID jobOrderId,
    Instant handoverTime,
    String recipientHandle,
    String recipientSquadron,
    @Nullable UserReferenceDto executingUser,
    @Nullable SquadronReferenceDto executingSquadron,
    List<JobOrderHandoverItemDto> items,
    Long version) {}
