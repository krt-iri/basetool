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

package de.greluc.krt.profit.basetool.frontend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Owner of the per-user "active OrgUnit" preference (admin and non-admin alike). The state lives in
 * the frontend's Redis-backed Spring Session — NOT in the backend — because backend REST calls
 * relay only the OAuth2 bearer token and no session cookies, so a backend-side {@code
 * HttpSession.setAttribute} would be lost as soon as the response returned. The active OrgUnit is
 * propagated to the backend on every API call via the {@code X-Active-Org-Unit-Id} request header
 * (with {@code X-Active-Squadron-Id} kept as a one-release alias — see {@code
 * ActiveSquadronRelayFilter} on the frontend and {@link
 * de.greluc.krt.profit.basetool.backend.service.OwnerScopeService#ACTIVE_ORG_UNIT_HEADER} / {@code
 * ACTIVE_SQUADRON_HEADER} on the backend); the backend honours the pinned id only when it matches
 * one of the caller's actual memberships (non-admin path) or is set by an admin (admin path).
 *
 * <p>Class-level gate is {@code isAuthenticated()} since R5.e widened the switcher to every
 * authenticated user with &gt;1 membership. The backend independently re-validates the pin against
 * the caller's actual memberships, so a spoofed POST cannot make a single-Staffel user appear to
 * pin an OrgUnit they do not belong to.
 */
@Controller
@RequestMapping("/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeFrontendController {

  /**
   * HTTP session attribute name under which the caller's currently selected OrgUnit lives. Mirrored
   * on the backend's {@link de.greluc.krt.profit.basetool.backend.service.OwnerScopeService} via
   * the {@code X-Active-Org-Unit-Id} request header set by the {@code ActiveSquadronRelayFilter};
   * the actual storage is the frontend's Spring Session (Redis-backed in dev/prod), NOT the
   * backend's request-scoped session.
   *
   * <p>R5.e renamed the key from {@code iridium.activeSquadronId} to {@code
   * iridium.activeOrgUnitId}. Existing admin sessions stored under the old key are NOT migrated —
   * the next switcher interaction overwrites the new key, and the old session attribute simply goes
   * unused until session expiry. Acceptable papercut because the only effect of "losing" the pin is
   * that admins are temporarily back in all-OrgUnits mode.
   */
  public static final String ACTIVE_ORG_UNIT_SESSION_KEY = "iridium.activeOrgUnitId";

  /**
   * Legacy session attribute name used pre-R5.e. Kept as a constant for traceability — the value is
   * no longer written; readers may still consult it during the migration soak as a fallback for
   * existing admin sessions.
   *
   * @deprecated R5.e renamed to {@link #ACTIVE_ORG_UNIT_SESSION_KEY}. Will be removed once the
   *     destructive cleanup release lands.
   */
  @Deprecated public static final String ACTIVE_SQUADRON_SESSION_KEY = "iridium.activeSquadronId";

  /**
   * R5.e replacement endpoint for {@link #setActiveSquadron}. Sets or clears the caller's active
   * OrgUnit selection in the frontend session. {@code orgUnitId} blank/empty clears the selection
   * (admin returns to "all OrgUnits" mode; non-admin returns to "union of memberships"); a
   * non-blank UUID activates that OrgUnit. The redirect makes the next page render see the new
   * context; the backend learns about the change on the next API call through the {@code
   * X-Active-Org-Unit-Id} header.
   *
   * <p>The frontend does not validate that the picked OrgUnit is actually one of the caller's
   * memberships — the backend's {@link
   * de.greluc.krt.profit.basetool.backend.service.OwnerScopeService#currentScopePredicate()}
   * re-validates the pin against the membership union and silently falls back to the union read if
   * the pin is foreign. Defence in depth against a spoofed POST.
   *
   * @param orgUnitId the OrgUnit to activate, blank/null to clear; never silently rejected.
   * @param referer optional referer field used to redirect back; defaults to {@code /} when
   *     missing.
   * @param request HTTP request injected by Spring; never {@code null}.
   * @param redirectAttributes flash attribute carrier for the success toast.
   * @return redirect view to the referring page so the next render sees the new context.
   */
  @PostMapping("/active-org-unit")
  public RedirectView setActiveOrgUnit(
      @RequestParam(value = "orgUnitId", required = false) @Nullable String orgUnitId,
      @RequestParam(value = "_referer", required = false) @Nullable String referer,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    return applyActiveOrgUnitSelection(orgUnitId, referer, request, redirectAttributes);
  }

  /**
   * Legacy R5.e endpoint kept as a one-release alias for {@link #setActiveOrgUnit}. Existing admin
   * browser tabs cached against the old path stay functional during the soak window. Reads the
   * legacy {@code squadronId} param name and the new {@code orgUnitId} alias, then delegates to
   * {@link #applyActiveOrgUnitSelection}. The endpoint is removed once the soak window closes and
   * the legacy form action has not been seen in access logs for a release cycle.
   *
   * @param squadronId legacy parameter name for the OrgUnit to activate.
   * @param orgUnitId new parameter name; honoured if both are sent.
   * @param referer optional referer field used to redirect back.
   * @param request HTTP request injected by Spring.
   * @param redirectAttributes flash attribute carrier for the success toast.
   * @return redirect view to the referring page.
   * @deprecated R5.e replacement is {@link #setActiveOrgUnit}.
   */
  @Deprecated
  @PostMapping("/active-squadron")
  public RedirectView setActiveSquadron(
      @RequestParam(value = "squadronId", required = false) @Nullable String squadronId,
      @RequestParam(value = "orgUnitId", required = false) @Nullable String orgUnitId,
      @RequestParam(value = "_referer", required = false) @Nullable String referer,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    String effective = (orgUnitId != null && !orgUnitId.isBlank()) ? orgUnitId : squadronId;
    return applyActiveOrgUnitSelection(effective, referer, request, redirectAttributes);
  }

  /**
   * Shared implementation of the legacy and the R5.e endpoint. Writes the chosen OrgUnit id to
   * {@link #ACTIVE_ORG_UNIT_SESSION_KEY} on the frontend session (or clears it on blank input) and
   * emits the {@code orgUnit.switcher.activated} / {@code orgUnit.switcher.cleared} flash toast.
   *
   * @param rawOrgUnitId the OrgUnit id to activate, blank/null to clear.
   * @param referer optional referer field used to redirect back.
   * @param request HTTP request injected by Spring.
   * @param redirectAttributes flash attribute carrier for the success toast.
   * @return redirect view to the referring page.
   */
  private RedirectView applyActiveOrgUnitSelection(
      @Nullable String rawOrgUnitId,
      @Nullable String referer,
      HttpServletRequest request,
      RedirectAttributes redirectAttributes) {
    HttpSession session = request.getSession(true);
    if (rawOrgUnitId == null || rawOrgUnitId.isBlank()) {
      session.removeAttribute(ACTIVE_ORG_UNIT_SESSION_KEY);
      // Defensive: also drop the legacy attribute so a session that pre-dates R5.e cannot
      // resurrect a stale pin via the relay filter's legacy-key fallback path.
      session.removeAttribute(ACTIVE_SQUADRON_SESSION_KEY);
      redirectAttributes.addFlashAttribute("toastSuccess", "orgUnit.switcher.cleared");
    } else {
      // Store as the UUID's canonical string form so the Redis-backed Spring Session can
      // round-trip the value without serializer ambiguity. Spring Session's default
      // JdkSerializationRedisSerializer plus the JSON wrapper in some configurations can
      // change a UUID instance into a String on deserialization — storing the String
      // representation up front avoids that brittleness and matches how the readers parse
      // it back.
      UUID parsed = UUID.fromString(rawOrgUnitId.trim());
      session.setAttribute(ACTIVE_ORG_UNIT_SESSION_KEY, parsed.toString());
      // Mirror to the legacy key for one release so any code path that still reads it (or any
      // session shared between an updated and a non-updated frontend during a rolling deploy)
      // observes the same value. R5.e cleanup release removes this line.
      session.setAttribute(ACTIVE_SQUADRON_SESSION_KEY, parsed.toString());
      redirectAttributes.addFlashAttribute("toastSuccess", "orgUnit.switcher.activated");
    }
    return new RedirectView(referer != null && !referer.isBlank() ? referer : "/");
  }
}
