package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend write payload for the operation payout-status toggle. Carries the
 * opaque participant key and the new flag value the user clicked, forwarded verbatim by the page
 * controller to {@code PUT /api/v1/operations/{id}/payouts/paid-out}.
 *
 * @param participantKey opaque participant key from {@code OperationPayoutDto.participantId}
 * @param paidOut new value for the paid-out flag
 */
public record OperationPayoutStatusUpdateDto(String participantKey, boolean paidOut) {}
