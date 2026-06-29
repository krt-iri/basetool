/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.model.dto.request;

import de.greluc.krt.profit.basetool.backend.validation.WholeNumber;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Write payload for booking a deposit (REQ-BANK-004/-005): money entered the bank and physically
 * landed with the named holder. The amount is whole-aUEC and strictly positive — the sign is
 * determined by the transaction type, never by the caller.
 *
 * <p><strong>Split deposit (REQ-BANK-043).</strong> When {@link #splitEnabled} is set the caller
 * additionally provides a whole-percent {@link #splitPercent} (1–100): that percentage of the gross
 * is distributed <em>evenly by count</em> across all active squadron accounts (excluding the named
 * account), and the named account is credited only the remainder. The percentage applies to the
 * whole gross; the resulting per-account amounts stay whole aUEC and sum back to the gross exactly
 * (largest-remainder distribution, see {@code BankLedgerService}). Both fields are absent (a plain
 * single-account deposit) unless the caller opts in.
 *
 * @param accountId the receiving account
 * @param holderId the player who physically received the money (REQ-BANK-003)
 * @param amount whole-aUEC amount, at least 1
 * @param note optional free-text note for the booking history and statements
 * @param splitEnabled whether to distribute {@link #splitPercent} of the gross across all squadron
 *     accounts (REQ-BANK-043)
 * @param splitPercent the whole-percent (1–100) of the gross to distribute; required when {@link
 *     #splitEnabled}, ignored otherwise
 */
public record BankDepositRequest(
    @NotNull UUID accountId,
    @NotNull UUID holderId,
    @NotNull @DecimalMin("1") @DecimalMax("1000000000000.0") @WholeNumber BigDecimal amount,
    @Nullable @Size(max = 500) String note,
    boolean splitEnabled,
    @Nullable @DecimalMin("1") @DecimalMax("100") @WholeNumber BigDecimal splitPercent) {

  /**
   * Cross-field rule (REQ-BANK-043): a split deposit must carry a percentage; a non-split deposit
   * carries none. The numeric range/whole-number of {@link #splitPercent} is enforced by its own
   * field constraints — this only pins the presence/absence relationship to {@link #splitEnabled}.
   *
   * @return {@code true} when the split flag and the percentage are consistent
   */
  @AssertTrue(message = "A split deposit requires a percentage; a non-split deposit must omit it")
  public boolean isSplitConfigConsistent() {
    return splitEnabled ? splitPercent != null : splitPercent == null;
  }

  /**
   * Convenience constructor for a plain single-account deposit (no split): delegates to the
   * canonical constructor with the split disabled. Keeps the many call sites and tests that predate
   * REQ-BANK-043 unchanged.
   *
   * @param accountId the receiving account
   * @param holderId the player who physically received the money
   * @param amount whole-aUEC amount, at least 1
   * @param note optional free-text note
   */
  public BankDepositRequest(
      @NotNull UUID accountId,
      @NotNull UUID holderId,
      @NotNull BigDecimal amount,
      @Nullable String note) {
    this(accountId, holderId, amount, note, false, null);
  }
}
