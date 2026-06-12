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

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.BankAccount;
import de.greluc.krt.iri.basetool.backend.model.BankAccountStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.BankDashboardAccountDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankDashboardDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BankDashboardTotalsDto;
import de.greluc.krt.iri.basetool.backend.model.projection.BankAccountBalance;
import de.greluc.krt.iri.basetool.backend.model.projection.BankPostingSlice;
import de.greluc.krt.iri.basetool.backend.repository.BankAccountRepository;
import de.greluc.krt.iri.basetool.backend.repository.BankPostingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * round-trips (REQ-DATA-003).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BankDashboardService {

  /** Length of the dashboard trend window in days (REQ-BANK-016). */
  private static final int WINDOW_DAYS = 30;

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
    Instant cutoff = Instant.now().minusSeconds((long) WINDOW_DAYS * 24 * 3600);
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
              sparkline(balance, delta, accountSlices)));
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

  /**
   * Derives the end-of-day balance series of the last {@value WINDOW_DAYS} days from the window's
   * posting slices: the series starts at {@code balance - delta} (the balance at window start) and
   * walks the daily nets; the last value equals the current balance. Days are bucketed in UTC,
   * matching the ledger's storage zone.
   *
   * @param balance the account's current balance
   * @param delta the net change inside the window
   * @param slices the account's posting slices inside the window
   * @return {@value WINDOW_DAYS} end-of-day balances, oldest first
   */
  private static List<BigDecimal> sparkline(
      @NotNull BigDecimal balance,
      @NotNull BigDecimal delta,
      @NotNull List<BankPostingSlice> slices) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Map<LocalDate, BigDecimal> dailyNet =
        slices.stream()
            .collect(
                Collectors.groupingBy(
                    slice -> slice.createdAt().atZone(ZoneOffset.UTC).toLocalDate(),
                    Collectors.reducing(
                        BigDecimal.ZERO, BankPostingSlice::amount, BigDecimal::add)));
    List<BigDecimal> series = new ArrayList<>(WINDOW_DAYS);
    BigDecimal running = balance.subtract(delta);
    for (int i = WINDOW_DAYS - 1; i >= 0; i--) {
      LocalDate day = today.minusDays(i);
      running = running.add(dailyNet.getOrDefault(day, BigDecimal.ZERO));
      series.add(running);
    }
    return series;
  }
}
