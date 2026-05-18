package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.service.SquadronScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for "me" - per-request preferences of the currently authenticated principal that
 * live in the Spring Session rather than in a JPA entity. Today there is exactly one such
 * preference: the admin's squadron switcher (the squadron context the admin opted into).
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MeController {

  private final SquadronScopeService squadronScopeService;

  /**
   * Returns the squadron context that the backend currently applies to staffel-scoped queries for
   * this request. For admins this is the session-stored switcher selection; for everyone else this
   * is the user's persistent home squadron. The {@code squadronId} is {@code null} when the admin
   * is in "all squadrons" mode or the user has no assigned squadron.
   *
   * @return current effective squadron context, never {@code null}.
   */
  @GetMapping("/active-squadron")
  public ActiveSquadronResponse getActiveSquadron() {
    return new ActiveSquadronResponse(squadronScopeService.currentSquadronId().orElse(null));
  }

  /**
   * Sets the admin's active squadron. Admin-only - the squadron switcher is an admin convenience;
   * non-admins always operate in their persistent home squadron and cannot deviate.
   *
   * @param request typed body carrying the squadron UUID; never {@code null}.
   * @return empty 204 response on success.
   */
  @PutMapping("/active-squadron")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> setActiveSquadron(
      @RequestBody @Valid @NotNull SetActiveSquadronRequest request) {
    squadronScopeService.setActiveSquadron(request.squadronId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Clears the admin's active squadron selection so the admin returns to the "all squadrons" view.
   * Admin-only for the same reason as {@link #setActiveSquadron(SetActiveSquadronRequest)}.
   *
   * @return empty 204 response on success.
   */
  @DeleteMapping("/active-squadron")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> clearActiveSquadron() {
    squadronScopeService.setActiveSquadron(null);
    return ResponseEntity.noContent().build();
  }

  /**
   * Body for {@code PUT /api/v1/me/active-squadron}: the squadron UUID the admin wants to operate
   * in. Use {@code DELETE} (not a body with {@code null}) to clear the selection.
   *
   * @param squadronId UUID of the squadron to activate; required.
   */
  public record SetActiveSquadronRequest(@NotNull UUID squadronId) {}

  /**
   * Response for {@code GET /api/v1/me/active-squadron}: the resolved effective squadron context
   * for the current request. {@code null} means the admin is viewing all squadrons (or the user has
   * no assigned squadron).
   *
   * @param squadronId effective squadron UUID, or {@code null}.
   */
  public record ActiveSquadronResponse(@Nullable UUID squadronId) {}
}
