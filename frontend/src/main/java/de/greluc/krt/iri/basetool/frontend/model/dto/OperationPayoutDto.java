package de.greluc.krt.iri.basetool.frontend.model.dto;

import de.greluc.krt.iri.basetool.frontend.model.PayoutPreference;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Frontend mirror of the backend {@code OperationPayoutDto} returned by {@code
 * /api/v1/operations/{id}/payouts}. Carries the time-share percentage, the computed money number
 * for the participant ({@code personalExpenses} reimbursement + {@code shareAmount} pool split),
 * and the mission-manager-set paid-out audit fields.
 *
 * @param participantId opaque participant key — user UUID stringified or {@code "guest_<name>"}
 * @param participantName the participant's display name (for the table column)
 * @param participationPercentage clamped attendance-time share, 0–100, two decimals
 * @param payoutPreference {@code PAYOUT} or {@code DONATE} (sticky DONATE across the operation)
 * @param personalExpenses out-of-pocket expenses attributable to the participant (&gt;= 0)
 * @param shareAmount totalSum × percentage / 100, or zero for DONATE
 * @param payoutAmount {@code personalExpenses + shareAmount} — pre-computed for display
 * @param paidOut whether the mission manager has marked this participant as already paid
 * @param paidOutAt timestamp of the last paid-out transition ({@code null} when never set)
 * @param paidOutByName effective name of the auditor that flipped the flag, or {@code null}
 */
public record OperationPayoutDto(
    String participantId,
    String participantName,
    double participationPercentage,
    PayoutPreference payoutPreference,
    BigDecimal personalExpenses,
    BigDecimal shareAmount,
    BigDecimal payoutAmount,
    boolean paidOut,
    Instant paidOutAt,
    String paidOutByName) {}
