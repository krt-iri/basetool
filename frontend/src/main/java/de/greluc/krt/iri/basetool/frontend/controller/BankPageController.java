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

package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.BankAccountDetailDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankAccountDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankBookingDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankDashboardAccountDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankDashboardDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.BankHolderDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Renders the bank area's read surfaces (epic #556): the dashboard ({@code /bank}, D1 card grid,
 * REQ-BANK-016) and the account detail ({@code /bank/accounts/{id}}, K1 two-column layout). The
 * backend decides every visibility/capability question — this controller only fetches, scales the
 * sparkline series into SVG polyline points and fills the model.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class BankPageController {

  /** ViewBox width of the dashboard sparkline (matches the D1 mockup's 96x26 SVG). */
  private static final double SPARK_WIDTH = 96d;

  /** ViewBox height of the dashboard sparkline. */
  private static final double SPARK_HEIGHT = 26d;

  /** Vertical padding inside the sparkline viewBox so extremes do not clip. */
  private static final double SPARK_PAD = 2d;

  private final BackendApiClient backendApiClient;

  /**
   * One dashboard card with its pre-scaled sparkline polyline, ready for the template.
   *
   * @param account the backend card payload
   * @param sparklinePoints SVG polyline {@code points} attribute value scaled to the 96x26 viewBox;
   *     {@code null} when the series is empty
   * @param flat {@code true} when the 30-day series never changes (renders the muted flat line)
   */
  public record BankDashboardCardView(
      BankDashboardAccountDto account, String sparklinePoints, boolean flat) {}

  /**
   * Renders the bank dashboard (REQ-BANK-016): one KPI card per visible account plus the
   * management-only totals strip.
   *
   * @param model Spring MVC model
   * @return the dashboard template
   */
  @GetMapping("/bank")
  @PreAuthorize("hasRole('BANK_EMPLOYEE')")
  public String dashboard(Model model) {
    BankDashboardDto dashboard =
        backendApiClient.get("/api/v1/bank/dashboard", BankDashboardDto.class);
    List<BankDashboardCardView> cards =
        dashboard == null
            ? List.of()
            : dashboard.accounts().stream().map(BankPageController::toCardView).toList();
    model.addAttribute("dashboard", dashboard);
    model.addAttribute("cards", cards);
    model.addAttribute("now", java.time.Instant.now().toString());
    return "bank-dashboard";
  }

  /**
   * Renders the account detail page (K1): facts strip, paged booking history, the permanent holder
   * distribution and the booking modals. The modal selects need the holder registry and the
   * caller's visible accounts (transfer destinations) — both fetched here so the page works without
   * follow-up AJAX reads.
   *
   * @param id the account id
   * @param page zero-based booking page
   * @param fragment when {@code "bookings"} only the paged booking-history fragment is rendered
   *     (AJAX pager swap, REQ-FE-002), skipping the holder/account round-trips the modals need;
   *     otherwise the full page is returned
   * @param model Spring MVC model
   * @return the detail template, or its {@code bookings} fragment for an AJAX swap
   */
  @GetMapping("/bank/accounts/{id}")
  @PreAuthorize("hasRole('BANK_EMPLOYEE')")
  public String accountDetail(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) String fragment,
      Model model) {
    if ("bookings".equals(fragment)) {
      return bookingsFragment(id, page, model);
    }
    int effectivePage = page == null || page < 0 ? 0 : page;
    BankAccountDetailDto detail =
        backendApiClient.get("/api/v1/bank/accounts/" + id, BankAccountDetailDto.class);
    PageResponse<BankBookingDto> bookings = fetchBookings(id, effectivePage);
    List<BankHolderDto> holders =
        backendApiClient.get(
            "/api/v1/bank/holders", new ParameterizedTypeReference<List<BankHolderDto>>() {});
    PageResponse<BankAccountDto> accounts =
        backendApiClient.get(
            "/api/v1/bank/accounts?size=500", new ParameterizedTypeReference<>() {});

    BigDecimal balance = detail != null ? detail.account().balance() : BigDecimal.ZERO;
    model.addAttribute("detail", detail);
    model.addAttribute("bookings", bookings);
    model.addAttribute("holders", holders == null ? List.<BankHolderDto>of() : holders);
    model.addAttribute(
        "activeHolders",
        holders == null
            ? List.<BankHolderDto>of()
            : holders.stream().filter(BankHolderDto::active).toList());
    model.addAttribute(
        "transferTargets",
        accounts == null
            ? List.<BankAccountDto>of()
            : accounts.content().stream()
                .filter(a -> !a.id().equals(id))
                .filter(a -> "ACTIVE".equals(a.status()))
                .toList());
    model.addAttribute("distributionPercents", distributionPercents(detail, balance));
    model.addAttribute("paginationBaseUrl", "/bank/accounts/" + id);
    return "bank-account-detail";
  }

  /**
   * Renders just the paged booking-history block for an AJAX pager swap (REQ-FE-002). Fetches only
   * the requested bookings page — the account detail, holder registry and transfer-target accounts
   * the full page loads (only the modals need them) are skipped. A backend failure degrades to an
   * empty page so the swapped-in fragment shows its empty state rather than injecting an error page
   * into the sub-table; the reverse-button (delegated) and {@code .utc-time} localiser (re-run on
   * {@code krt:swapped}) keep working on the swapped-in rows.
   *
   * @param id the account id
   * @param page zero-based booking page (clamped to 0)
   * @param model Spring MVC model populated with {@code bookings} and {@code paginationBaseUrl}
   * @return the {@code bank-account-detail :: bookings} fragment view
   */
  private String bookingsFragment(UUID id, Integer page, Model model) {
    int effectivePage = page == null || page < 0 ? 0 : page;
    PageResponse<BankBookingDto> bookings;
    try {
      bookings = fetchBookings(id, effectivePage);
    } catch (Exception e) {
      log.error("Error loading bookings fragment for account {}", id, e);
      bookings = null;
    }
    model.addAttribute("bookings", bookings);
    model.addAttribute("paginationBaseUrl", "/bank/accounts/" + id);
    return "bank-account-detail :: bookings";
  }

  /**
   * Fetches one page of an account's booking history (page size 20) from the backend transactions
   * endpoint — the single source of the booking query shared by the full-page render and the {@link
   * #bookingsFragment} AJAX swap.
   *
   * @param id the account id whose transactions to page through
   * @param page zero-based, already-clamped page index
   * @return the requested bookings page envelope
   */
  private PageResponse<BankBookingDto> fetchBookings(UUID id, int page) {
    return backendApiClient.get(
        UriComponentsBuilder.fromPath("/api/v1/bank/accounts/" + id + "/transactions")
            .queryParam("page", page)
            .queryParam("size", 20)
            .toUriString(),
        new ParameterizedTypeReference<>() {});
  }

  /**
   * Pre-computes the integer percentage per holder slice for the bars and the stack segments —
   * Thymeleaf should not carry BigDecimal division logic.
   *
   * @param detail the loaded detail payload
   * @param balance the account balance (the 100% base)
   * @return holder id to integer percentage (0..100)
   */
  private static java.util.Map<UUID, Integer> distributionPercents(
      BankAccountDetailDto detail, @NotNull BigDecimal balance) {
    java.util.Map<UUID, Integer> percents = new java.util.LinkedHashMap<>();
    if (detail == null || balance.signum() == 0) {
      return percents;
    }
    for (var slice : detail.holderDistribution()) {
      BigDecimal pct =
          slice.amount().multiply(BigDecimal.valueOf(100)).divide(balance, 0, RoundingMode.HALF_UP);
      percents.put(slice.holderId(), Math.max(0, Math.min(100, pct.intValue())));
    }
    return percents;
  }

  /**
   * Scales one card's end-of-day balance series into the 96x26 sparkline polyline (D1 mockup). A
   * flat series renders as the muted mid-height line, mirroring the mockup's zero-delta card.
   *
   * @param account the backend card payload
   * @return the card view with pre-computed points
   */
  private static BankDashboardCardView toCardView(@NotNull BankDashboardAccountDto account) {
    List<BigDecimal> series = account.sparkline();
    if (series == null || series.isEmpty()) {
      return new BankDashboardCardView(account, null, true);
    }
    BigDecimal min = series.getFirst();
    BigDecimal max = series.getFirst();
    for (BigDecimal v : series) {
      if (v.compareTo(min) < 0) {
        min = v;
      }
      if (v.compareTo(max) > 0) {
        max = v;
      }
    }
    boolean flat = max.compareTo(min) == 0;
    double range = flat ? 1d : max.subtract(min).doubleValue();
    double stepX = series.size() > 1 ? SPARK_WIDTH / (series.size() - 1) : SPARK_WIDTH;
    StringBuilder points = new StringBuilder();
    for (int i = 0; i < series.size(); i++) {
      double x = series.size() > 1 ? i * stepX : SPARK_WIDTH / 2;
      double normalized = flat ? 0.5d : series.get(i).subtract(min).doubleValue() / range;
      double y = SPARK_HEIGHT - SPARK_PAD - normalized * (SPARK_HEIGHT - 2 * SPARK_PAD);
      if (i > 0) {
        points.append(' ');
      }
      points.append(Math.round(x * 10) / 10.0).append(',').append(Math.round(y * 10) / 10.0);
    }
    return new BankDashboardCardView(account, points.toString(), flat);
  }
}
