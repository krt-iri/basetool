package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Write-side payload for the {@code PUT /api/v1/operations/{id}/payouts/paid-out} endpoint that
 * lets a mission manager flip the per-participant paid-out flag.
 *
 * <p>The flag is last-writer-wins: there is no client-supplied optimistic-lock version because the
 * only mutable field is a boolean and concurrent toggles are intrinsically idempotent. Repeated
 * calls with the same {@code paidOut} value are no-ops on the data but still bump {@code paidOutAt}
 * / {@code paidOutByUser} so the audit trail always reflects the last toggling click.
 *
 * @param participantKey opaque participant key from {@link OperationPayoutDto#participantId()}
 * @param paidOut new value for the paid-out flag
 */
public record OperationPayoutStatusUpdateDto(
    @NotBlank @Size(max = 255) String participantKey, boolean paidOut) {}
