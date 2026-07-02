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

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Resolves the responsible (processing) and requesting (customer) org units of a job order from the
 * create/update picker output, applying the profit-eligibility and guest-fallback rules. Extracted
 * from {@code JobOrderService} (L2, #921) so the org-unit resolution concern lives on its own,
 * behind the same rules verbatim.
 *
 * <p>Read-only: every method only looks up {@link OrgUnit}s and applies validation; it holds no
 * state and never mutates an entity, so it runs inside the caller's transaction with no annotation
 * of its own.
 */
@Service
@RequiredArgsConstructor
public class JobOrderOrgUnitResolver {

  /**
   * System-setting key holding the intake Spezialkommando id that anonymous/guest order creations
   * are routed to when their responsible pick is absent or not profit-eligible.
   */
  private static final String INTAKE_SK_SETTING_KEY = "job_order.intake_special_command_id";

  /** Distinguishes authenticated callers (strict validation) from guests (forgiving fallback). */
  private final AuthHelperService authHelperService;

  /** Resolves picker output ids to managed {@link OrgUnit} entities. */
  private final OrgUnitRepository orgUnitRepository;

  /** Reads the configured intake Spezialkommando id for the guest fallback. */
  private final SystemSettingService systemSettingService;

  /**
   * Resolves the responsible (processing) org unit for a freshly-created job order.
   *
   * <ul>
   *   <li>Anonymous / guest callers (the public request form is {@code permitAll}) may pick any
   *       <em>profit-eligible</em> squadron or Spezialkommando from the create form's responsible
   *       picker, and that pick is honoured. When the picker output is absent, unresolvable, or
   *       points at a non-profit unit, the order routes to the configured intake Spezialkommando
   *       ({@link #INTAKE_SK_SETTING_KEY}) as a forgiving fallback — so a guest can still never
   *       direct work to a unit that has not opted in to processing orders.
   *   <li>Authenticated callers must supply a {@code responsibleOrgUnitId} that resolves to a
   *       profit-eligible squadron or Spezialkommando — only Profit-side units process orders.
   * </ul>
   *
   * @param responsibleOrgUnitId picker output from the create DTO; required for authenticated
   *     callers, honoured-when-profit-eligible (else intake-SK fallback) for guests.
   * @return the resolved, profit-eligible responsible org unit; never {@code null}.
   * @throws BadRequestException when an authenticated caller's id is missing/unresolvable or not
   *     profit-eligible, or no intake SK is configured for a guest creation that falls back.
   */
  @NotNull
  public OrgUnit resolveResponsibleOrgUnit(@Nullable UUID responsibleOrgUnitId) {
    if (!authHelperService.isAuthenticated()) {
      // Guest (public request form): honour a profit-eligible pick from the responsible picker,
      // otherwise route to the configured intake Spezialkommando. A non-profit or unresolvable id
      // is
      // not a 400 here — the public form stays forgiving and silently falls back rather than
      // surfacing a validation gate to an anonymous probe. The profit-eligibility guard is
      // preserved, so a guest still cannot direct work to a unit that has not opted in.
      if (responsibleOrgUnitId != null) {
        OrgUnit picked = orgUnitRepository.findById(responsibleOrgUnitId).orElse(null);
        if (picked != null && picked.isProfitEligible()) {
          return picked;
        }
      }
      return resolveIntakeSpecialCommand();
    }
    if (responsibleOrgUnitId == null) {
      throw new BadRequestException("responsibleOrgUnitId is required.");
    }
    OrgUnit orgUnit =
        orgUnitRepository
            .findById(responsibleOrgUnitId)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "responsibleOrgUnitId does not resolve to a known org unit: "
                            + responsibleOrgUnitId));
    if (!orgUnit.isProfitEligible()) {
      throw new BadRequestException(
          "The selected responsible org unit is not profit-eligible and cannot process orders: "
              + responsibleOrgUnitId);
    }
    return orgUnit;
  }

  /**
   * Resolves the configured intake Spezialkommando that anonymous/guest order creations are routed
   * to (system setting {@link #INTAKE_SK_SETTING_KEY}). The setting is seeded empty by V128; until
   * an admin selects an SK, guest creation is refused with a 400.
   *
   * @return the configured intake org unit; never {@code null}.
   * @throws BadRequestException when the setting is unset/blank/malformed or no longer resolves.
   */
  @NotNull
  private OrgUnit resolveIntakeSpecialCommand() {
    String raw =
        systemSettingService
            .getSettingValue(INTAKE_SK_SETTING_KEY)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "No intake Spezialkommando is configured; an admin must set it in system"
                            + " settings before guests can create orders."));
    UUID intakeId;
    try {
      intakeId = UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException("Configured intake Spezialkommando id is malformed.");
    }
    return orgUnitRepository
        .findById(intakeId)
        .orElseThrow(
            () -> new BadRequestException("Configured intake Spezialkommando no longer exists."));
  }

  /**
   * Resolves the requesting (customer / Auftraggeber) org unit from the picker output. Any org unit
   * is accepted — Staffel, Spezialkommando, Bereich or Organisationsleitung (epic #692): there is
   * no profit-eligibility or kind restriction on who may be named the customer (unlike the
   * responsible unit, which must be a profit-eligible Staffel/SK). Mandatory: the create/update
   * DTOs always carry it.
   *
   * @param requestingOrgUnitId picker output from the DTO.
   * @return the resolved requesting org unit; never {@code null}.
   * @throws BadRequestException when the id is missing or does not resolve to a known org unit.
   */
  @NotNull
  public OrgUnit resolveRequestingOrgUnit(@Nullable UUID requestingOrgUnitId) {
    if (requestingOrgUnitId == null) {
      throw new BadRequestException("requestingOrgUnitId is required.");
    }
    return orgUnitRepository
        .findById(requestingOrgUnitId)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "requestingOrgUnitId does not resolve to a known org unit: "
                        + requestingOrgUnitId));
  }
}
