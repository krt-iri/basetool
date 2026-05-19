package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.service.SquadronScopeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only echo of the squadron context that the backend currently applies to staffel-scoped
 * queries. The active squadron preference is owned by the frontend (Redis-backed Spring Session via
 * {@code MeFrontendController}); the backend learns about the admin's choice on every API call
 * through the {@code X-Active-Squadron-Id} header relayed by the frontend's WebClient.
 *
 * <p>This controller used to expose {@code PUT}/{@code DELETE} mutators that stored the selection
 * in the backend's {@code HttpSession}, but that was effectively a no-op: REST calls from the
 * frontend do not relay session cookies (only the OAuth2 bearer token), so each call created a
 * fresh backend session and the attribute was lost between requests. The mutators are gone; the
 * only remaining surface is the {@code GET} which reflects what the header for the current request
 * says.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeController {

  private final SquadronScopeService squadronScopeService;

  /**
   * Returns the squadron context that the backend currently applies to staffel-scoped queries for
   * this request. For admins this is the {@code X-Active-Squadron-Id} header value relayed by the
   * frontend; for everyone else this is the user's persistent home squadron. The {@code squadronId}
   * is {@code null} when the admin is in "all squadrons" mode or the user has no assigned squadron.
   *
   * @return current effective squadron context, never {@code null}.
   */
  @GetMapping("/active-squadron")
  public ActiveSquadronResponse getActiveSquadron() {
    return new ActiveSquadronResponse(squadronScopeService.currentSquadronId().orElse(null));
  }

  /**
   * Response for {@code GET /api/v1/me/active-squadron}: the resolved effective squadron context
   * for the current request. {@code null} means the admin is viewing all squadrons (or the user has
   * no assigned squadron).
   *
   * @param squadronId effective squadron UUID, or {@code null}.
   */
  public record ActiveSquadronResponse(@Nullable UUID squadronId) {}
}
