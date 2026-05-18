package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.PromotionEligibilityResponse;
import de.greluc.krt.iri.basetool.backend.service.PromotionEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for inspecting promotion eligibility.
 *
 * <p>The personal endpoints are filtered by JWT sub – callers never see another member's
 * eligibility through them. The {@code /user/{userId}} endpoint is restricted to {@code ADMIN} and
 * {@code OFFICER} so officers can drive promotion reviews from the management page.
 */
@RestController
@RequestMapping("/api/v1/promotion/eligibility")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(
    name = "Promotion Eligibility",
    description = "Evaluate whether a member meets the promotion requirements.")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class PromotionEligibilityController {

  private final PromotionEligibilityService service;

  /**
   * Returns the eligibility outcome for every configured rank transition for the calling user.
   *
   * @param auth the JWT-backed authentication, never {@code null} due to {@code isAuthenticated()}
   * @return one entry per configured transition, possibly empty
   */
  @GetMapping("/my")
  @Operation(summary = "Evaluate eligibility for the caller for every configured rank transition.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Per-transition eligibility for the caller."),
    @ApiResponse(responseCode = "401", description = "Authentication required.")
  })
  public List<PromotionEligibilityResponse> myEligibility(JwtAuthenticationToken auth) {
    return service.evaluateAllForUser(requireSub(auth));
  }

  /**
   * Returns the eligibility outcome for one specific rank transition for the calling user. Useful
   * for the "next promotion" widget which only cares about the user's current rank.
   *
   * @param fromRank the rank the caller currently holds
   * @param toRank the rank the caller would be promoted to
   * @param auth the JWT-backed authentication
   * @return the per-rule outcome plus an aggregate {@code eligible} flag
   */
  @GetMapping("/my/by-ranks")
  @Operation(summary = "Evaluate eligibility for the caller for a single rank transition.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Eligibility for the requested transition.")
  })
  public PromotionEligibilityResponse myEligibilityForRanks(
      @Parameter(description = "Current rank of the caller.") @RequestParam int fromRank,
      @Parameter(description = "Target rank for the promotion.") @RequestParam int toRank,
      JwtAuthenticationToken auth) {
    return service.evaluateForRanks(requireSub(auth), fromRank, toRank);
  }

  /**
   * Officer/admin view: returns the eligibility outcome for every configured rank transition for an
   * arbitrary member.
   *
   * @param userId the JWT-sub identifier of the member to inspect
   * @return one entry per configured transition, possibly empty
   */
  @GetMapping("/user/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Evaluate eligibility for another member (ADMIN/OFFICER only).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Per-transition eligibility for the target member."),
    @ApiResponse(responseCode = "403", description = "Insufficient permissions.")
  })
  public List<PromotionEligibilityResponse> eligibilityForUser(@PathVariable String userId) {
    return service.evaluateAllForUserAsAdmin(userId);
  }

  @NotNull
  private static String requireSub(JwtAuthenticationToken auth) {
    if (auth == null || auth.getToken() == null) {
      throw new AccessDeniedException("Missing JWT.");
    }
    Jwt jwt = auth.getToken();
    String sub = jwt.getSubject();
    if (sub == null || sub.isBlank()) {
      throw new AccessDeniedException("JWT does not contain a subject claim.");
    }
    return sub;
  }
}
