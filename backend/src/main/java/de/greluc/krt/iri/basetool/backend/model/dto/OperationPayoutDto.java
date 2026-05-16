package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.PayoutPreference;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data transfer record carrying one participant's row of the operation payout breakdown.
 *
 * <p>The amount fields obey the operation payout model: a participant's expenses (mission EXPENSE
 * finance entries where they are the participant + their refinery orders' {@code expenses +
 * otherExpenses}) are reimbursed off the top, and the remaining {@code totalSum} is split per
 * {@link #participationPercentage} across PAYOUT participants. DONATE participants keep their
 * {@link #personalExpenses} reimbursement (it is their own money) but contribute their {@link
 * #shareAmount} to the org. {@link #payoutAmount} is always {@code personalExpenses + shareAmount}
 * so the frontend can render a single number without re-deriving it.
 *
 * <p>The {@code paidOut*} block reflects the {@link
 * de.greluc.krt.iri.basetool.backend.model.OperationPayoutStatus} row that the mission-manager
 * toggle endpoint maintains: {@code paidOut=false}, no timestamp and no auditor name when no row
 * exists yet. {@link #paidOutByName} resolves to {@code User.effectiveName} or {@code null} when
 * the auditor has been deleted.
 *
 * @param participantId opaque participant key — user UUID stringified or {@code "guest_<name>"}
 * @param participantName the participant's display name (for the table column)
 * @param participationPercentage clamped attendance-time share, 0–100, two decimals
 * @param payoutPreference {@code PAYOUT} or {@code DONATE} (sticky DONATE across the operation)
 * @param personalExpenses out-of-pocket expenses attributable to the participant (always &gt;= 0)
 * @param shareAmount totalSum × percentage / 100, or {@link BigDecimal#ZERO} for DONATE
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
