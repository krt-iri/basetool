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

import de.greluc.krt.iri.basetool.frontend.model.form.MissionFinanceEntryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for mission finance-entry CRUD ({@code /missions/{id}/finance-entries/**}).
 *
 * <p>Carved out from {@link MissionPageController} so the file stays manageable. Validation
 * failures re-render the mission-detail view inline by delegating to {@code
 * missionPageController.missionDetail(...)} — that keeps the BindingResult request-scoped (avoiding
 * the Redis-FlashMap self-reference crash) and preserves the modal-open flag so the user sees the
 * form with errors instead of an empty page. The injected {@link MissionPageController} is a Spring
 * proxy, so its method-level {@code @PreAuthorize} still fires when called via this delegation.
 */
@Slf4j
@Controller
@RequestMapping("/missions/{id}/finance-entries")
@RequiredArgsConstructor
public class MissionFinancePageController {

  private final BackendApiClient backendApiClient;
  private final MissionPageController missionPageController;

  /**
   * Creates a finance entry on a mission. {@code permitAll()} reflects the project's guest-mode for
   * mission finances — the backend still gates write access at the JWT layer when needed.
   *
   * @param id mission id
   * @param form finance-entry form
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering (modal stays open)
   * @param redirectAttributes flash attributes carrier
   * @param principal OIDC user, may be {@code null} for guests
   * @return inline {@code mission-detail} view on validation failure, otherwise redirect
   */
  @PostMapping
  @PreAuthorize("permitAll()")
  public String addFinanceEntry(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("financeForm") MissionFinanceEntryForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "finance-entry-modal");
      return missionPageController.missionDetail(id, model, principal, null);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("missionId", id);
      body.put("participantId", form.getParticipantId());
      body.put("note", form.getNote());
      body.put("type", form.getType());
      body.put("amount", form.getAmount());

      backendApiClient.post("/api/v1/finance-entries", body, Void.class, principal == null);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Add finance entry failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.finance.add");
    }
    return "redirect:/missions/" + id;
  }

  /**
   * Updates a finance entry. The form carries the optimistic-lock version. Authenticated-only —
   * guest write is restricted to {@code POST} (create) above.
   *
   * @param id mission id (path)
   * @param entryId finance entry id (path)
   * @param form finance-entry form (carries the version field)
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering
   * @param redirectAttributes flash attributes carrier
   * @param principal OIDC user
   * @return inline {@code mission-detail} view on validation failure, otherwise redirect
   */
  @PostMapping("/{entryId}/update")
  @PreAuthorize("isAuthenticated()")
  public String updateFinanceEntry(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID entryId,
      @Valid @ModelAttribute("financeForm") MissionFinanceEntryForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes,
      @AuthenticationPrincipal OidcUser principal) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("openModal", "edit-finance-entry-modal");
      model.addAttribute(
          "modalAction", "/missions/" + id + "/finance-entries/" + entryId + "/update");
      return missionPageController.missionDetail(id, model, principal, null);
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("note", form.getNote());
      body.put("type", form.getType());
      body.put("amount", form.getAmount());
      body.put("version", form.getVersion());

      backendApiClient.put("/api/v1/finance-entries/" + entryId, body, Void.class, false);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (Exception e) {
      log.error("Update finance entry failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.finance.update");
    }
    return "redirect:/missions/" + id;
  }

  /**
   * Deletes a finance entry.
   *
   * @param id mission id (path)
   * @param entryId finance entry id (path)
   * @param principal OIDC user
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /missions/{id}}
   */
  @PostMapping("/{entryId}/delete")
  @PreAuthorize("isAuthenticated()")
  public String deleteFinanceEntry(
      @PathVariable @NotNull UUID id,
      @PathVariable @NotNull UUID entryId,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/finance-entries/" + entryId, Void.class, false);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.delete");
    } catch (Exception e) {
      log.error("Delete finance entry failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.finance.delete");
    }
    return "redirect:/missions/" + id;
  }
}
