package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.service.OwnerScopeService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only echo of the org-unit context that the backend currently applies to staffel-scoped
 * queries. The active org-unit preference is owned by the frontend (Redis-backed Spring Session via
 * {@code MeFrontendController}); the backend learns about the caller's choice on every API call
 * through the {@code X-Active-Org-Unit-Id} header (legacy alias: {@code X-Active-Squadron-Id})
 * relayed by the frontend's WebClient.
 *
 * <p>This controller used to expose {@code PUT}/{@code DELETE} mutators that stored the selection
 * in the backend's {@code HttpSession}, but that was effectively a no-op: REST calls from the
 * frontend do not relay session cookies (only the OAuth2 bearer token), so each call created a
 * fresh backend session and the attribute was lost between requests. The mutators are gone; the
 * only remaining surface are the {@code GET}s which reflect what the header for the current request
 * says, plus the per-principal {@code GET /capabilities} UI flags.
 *
 * <p>Two paths exist for the active-context read endpoint during the SPEZIALKOMMANDO_PLAN.md §7.2
 * rename soak window: the new canonical {@code GET /active-org-unit} and the legacy alias {@code
 * GET /active-squadron}. Both return the same payload shape and are forwarded through the same
 * scope resolver — the alias exists so a frontend release that lags the backend rename keeps
 * working. The legacy path is dropped together with the {@code X-Active-Squadron-Id} header alias
 * in the destructive cleanup release.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeController {

  private final OwnerScopeService ownerScopeService;

  /**
   * Returns the org-unit context that the backend currently applies to staffel-scoped queries for
   * this request. For admins this is the {@code X-Active-Org-Unit-Id} header value relayed by the
   * frontend (legacy alias: {@code X-Active-Squadron-Id}); for non-admins with a pinned context the
   * header is honoured iff the pin matches one of their memberships; otherwise this is the user's
   * persistent home Staffel. The {@code orgUnitId} is {@code null} when the admin is in "all
   * OrgUnits" mode or the user has no assigned home Staffel.
   *
   * @return current effective org-unit context for the calling request; never {@code null}.
   */
  @GetMapping("/active-org-unit")
  public ActiveOrgUnitResponse getActiveOrgUnit() {
    return new ActiveOrgUnitResponse(ownerScopeService.currentOrgUnitId().orElse(null));
  }

  /**
   * Legacy alias for {@link #getActiveOrgUnit()} kept for one release window while frontend clients
   * migrate to the new path. Returns the legacy {@link ActiveSquadronResponse} shape so old
   * deserialisers keep working — the field is named {@code squadronId} but carries the same value
   * as the new endpoint's {@code orgUnitId}.
   *
   * @return current effective org-unit context for the calling request; never {@code null}.
   * @deprecated since SPEZIALKOMMANDO_PLAN.md §7.2 — call {@link #getActiveOrgUnit()} instead.
   */
  @Deprecated
  @GetMapping("/active-squadron")
  public ActiveSquadronResponse getActiveSquadron() {
    return new ActiveSquadronResponse(ownerScopeService.currentOrgUnitId().orElse(null));
  }

  /**
   * Per-principal UI capability flags the frontend uses to decide which optional menu entries to
   * show and which pages to redirect away from. Two flags today:
   *
   * <ul>
   *   <li>{@code canSeeBlueprintOverview} — whether the caller may open the org-unit blueprint
   *       availability overview (#364): {@code true} for admins, officers, and Spezialkommando
   *       leads. Reuses the exact gate the {@code /api/v1/personal-blueprints/overview} endpoints
   *       are class-gated by.
   *   <li>{@code canViewJobOrders} — whether the caller may enter the Job-Order area: {@code true}
   *       for admins and members of any profit-eligible org unit. Mirrors the backend gate folded
   *       into {@code OwnerScopeService.canSeeJobOrder} + the order-list short-circuit, so the
   *       hidden menu / redirect and the empty-list / 403 API stay in lockstep.
   * </ul>
   *
   * @return the caller's UI capability flags; never {@code null}.
   */
  @GetMapping("/capabilities")
  @Operation(summary = "Per-principal UI capability flags (blueprint-overview + job-order access).")
  public CapabilitiesResponse getCapabilities() {
    return new CapabilitiesResponse(
        ownerScopeService.canAccessBlueprintOverview(), ownerScopeService.canViewJobOrders());
  }

  /**
   * Response for {@code GET /api/v1/me/active-org-unit}: the resolved effective org-unit context
   * for the current request. {@code null} means the admin is viewing all OrgUnits (or the user has
   * no assigned home Staffel and no pinned context).
   *
   * @param orgUnitId effective OrgUnit UUID, or {@code null}.
   */
  public record ActiveOrgUnitResponse(@Nullable UUID orgUnitId) {}

  /**
   * Legacy response shape for the deprecated {@code GET /api/v1/me/active-squadron} alias. Carries
   * the same UUID value as {@link ActiveOrgUnitResponse#orgUnitId()} but exposes it under the
   * legacy field name {@code squadronId} so pre-R5.e clients keep deserialising correctly. Drops
   * together with the legacy endpoint in the destructive cleanup release.
   *
   * @param squadronId effective OrgUnit UUID, or {@code null}.
   */
  public record ActiveSquadronResponse(@Nullable UUID squadronId) {}

  /**
   * Response for {@code GET /api/v1/me/capabilities}: per-principal UI capability flags.
   *
   * @param canSeeBlueprintOverview {@code true} iff the caller may open the org-unit blueprint
   *     availability overview (admin, officer, or Spezialkommando lead).
   * @param canViewJobOrders {@code true} iff the caller may enter the Job-Order area (admin, or
   *     member of at least one profit-eligible org unit).
   */
  public record CapabilitiesResponse(boolean canSeeBlueprintOverview, boolean canViewJobOrders) {}
}
