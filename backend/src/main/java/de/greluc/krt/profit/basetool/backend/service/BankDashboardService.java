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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.model.BankAccount;
import de.greluc.krt.profit.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.profit.basetool.backend.model.dto.BankDashboardAccountDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankDashboardDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BankDashboardTotalsDto;
import de.greluc.krt.profit.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.profit.basetool.backend.model.projection.BankPostingSlice;
import de.greluc.krt.profit.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.profit.basetool.backend.repository.BankPostingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the bank dashboard payload (epic #556, REQ-BANK-016): one KPI card per visible account
 * with balance, 30-day delta and the daily balance series for the server-rendered sparkline, plus
 * the management-only totals strip. Everything derives from THREE statements — the account list,
 * one grouped balance query and one windowed posting-slice query — never from per-account
 * round-trips (REQ-DATA-003). The per-account delta and sparkline series are derived via {@link
 * BankTrendCalculator}, the same helper the org-unit balance page uses, so both surfaces show an
 * identical 30-day trend.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankDashboardService {

  private final BankAccountRepository accountRepository;
  private final BankPostingRepository postingRepository;

  /**
   * Assembles the dashboard for the calling user: management/admin see every account plus the
   * aggregate strip, employees see exactly their granted accounts and no totals (REQ-BANK-010).
   *
   * @param management whether the caller has the management perspective
   * @param userId the caller's user id (the employee filter)
   * @return the dashboard payload
   */
  public BankDashboardDto getDashboard(boolean management, @NotNull UUID userId) {
    List<BankAccount> accounts =
        management
            ? accountRepository.findAllByOrderByAccountNoAsc()
            : accountRepository.findAllGrantedTo(userId);
    List<UUID> ids = accounts.stream().map(BankAccount::getId).toList();

    Map<UUID, BigDecimal> balances =
        ids.isEmpty()
            ? Map.of()
            : postingRepository.accountBalances(ids).stream()
                .collect(
                    Collectors.toMap(BankAccountBalance::accountId, BankAccountBalance::balance));
    Instant cutoff = BankTrendCalculator.windowCutoff();
    Map<UUID, List<BankPostingSlice>> slices =
        ids.isEmpty()
            ? Map.of()
            : postingRepository.postingSlicesSince(ids, cutoff).stream()
                .collect(Collectors.groupingBy(BankPostingSlice::accountId));

    List<BankDashboardAccountDto> cards = new ArrayList<>(accounts.size());
    BigDecimal totalBalance = BigDecimal.ZERO;
    BigDecimal inflow = BigDecimal.ZERO;
    BigDecimal outflow = BigDecimal.ZERO;
    long active = 0;
    long closed = 0;
    for (BankAccount account : accounts) {
      BigDecimal balance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
      List<BankPostingSlice> accountSlices = slices.getOrDefault(account.getId(), List.of());
      BigDecimal delta = BigDecimal.ZERO;
      for (BankPostingSlice slice : accountSlices) {
        delta = delta.add(slice.amount());
        if (slice.amount().signum() > 0) {
          inflow = inflow.add(slice.amount());
        } else {
          outflow = outflow.add(slice.amount());
        }
      }
      cards.add(
          new BankDashboardAccountDto(
              account.getId(),
              account.getAccountNo(),
              account.getName(),
              account.getType(),
              account.getStatus(),
              balance,
              delta,
              BankTrendCalculator.sparkline(balance, delta, accountSlices)));
      totalBalance = totalBalance.add(balance);
      if (account.getStatus() == BankAccountStatus.ACTIVE) {
        active++;
      } else {
        closed++;
      }
    }

    BankDashboardTotalsDto totals =
        management
            ? new BankDashboardTotalsDto(totalBalance, inflow, outflow, active, closed)
            : null;
    return new BankDashboardDto(management, cards, totals);
  }
}
