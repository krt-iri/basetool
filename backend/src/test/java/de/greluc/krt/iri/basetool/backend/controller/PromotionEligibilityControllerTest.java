package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.dto.PromotionEligibilityResponse;
import de.greluc.krt.iri.basetool.backend.service.PromotionEligibilityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Pure-Mockito unit tests for {@link PromotionEligibilityController}. Unlike the topic / category /
 * level-content controllers this one carries non-trivial logic in {@code requireSub}: extract the
 * JWT subject claim, reject {@code null} authentication, {@code null} token, missing / blank {@code
 * sub}. Each of those four failure modes is pinned here because a regression silently drops the
 * data-isolation guarantee — the entire {@code /my} contract is "you see your own eligibility and
 * nobody else's", which relies on the JWT-sub being non-empty before it ever reaches the service.
 *
 * <p>The {@code /user/{userId}} branch is the officer/admin view; its access control comes from the
 * method-level {@code @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")} (a SecurityConfig concern,
 * not a unit-test concern), so we only verify the pass-through to {@code
 * evaluateAllForUserAsAdmin}.
 */
@ExtendWith(MockitoExtension.class)
class PromotionEligibilityControllerTest {

  @Mock private PromotionEligibilityService service;

  @InjectMocks private PromotionEligibilityController controller;

  // ── Test helpers ────────────────────────────────────────────────────────

  /**
   * Builds a minimal JwtAuthenticationToken whose {@code Jwt} carries the supplied {@code sub}
   * claim. Returning {@code sub == null} bypasses the {@code Jwt} subject and exercises the
   * blank-subject branch of {@code requireSub}.
   */
  private static JwtAuthenticationToken authWithSub(String sub) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(sub)
            .claim("sub", sub == null ? "" : sub)
            .build();
    return new JwtAuthenticationToken(jwt);
  }

  private static PromotionEligibilityResponse eligibility(String userId, int from, int to) {
    return new PromotionEligibilityResponse(userId, from, to, true, true, List.of());
  }

  // ── myEligibility ───────────────────────────────────────────────────────

  @Test
  void myEligibility_extractsSubFromJwtAndForwardsToService() {
    String sub = "alice-uuid";
    JwtAuthenticationToken auth = authWithSub(sub);
    List<PromotionEligibilityResponse> expected = List.of(eligibility(sub, 20, 19));
    when(service.evaluateAllForUser(sub)).thenReturn(expected);

    List<PromotionEligibilityResponse> result = controller.myEligibility(auth);

    assertThat(result).isSameAs(expected);
    verify(service).evaluateAllForUser(sub);
  }

  @Test
  void myEligibility_whenAuthIsNull_throwsAccessDeniedAndDoesNotCallService() {
    assertThatThrownBy(() -> controller.myEligibility(null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("JWT");
    verifyNoInteractions(service);
  }

  @Test
  void myEligibility_whenJwtSubIsBlank_throwsAccessDeniedAndDoesNotCallService() {
    // Use Mockito to fake a "valid" auth whose Jwt returns blank from getSubject — Jwt.Builder
    // refuses to build with subject(null), so we drop down to a manual mock.
    JwtAuthenticationToken auth = org.mockito.Mockito.mock(JwtAuthenticationToken.class);
    Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
    when(auth.getToken()).thenReturn(jwt);
    when(jwt.getSubject()).thenReturn("   ");

    assertThatThrownBy(() -> controller.myEligibility(auth))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("subject");
    verifyNoInteractions(service);
  }

  @Test
  void myEligibility_whenJwtSubIsNull_throwsAccessDeniedAndDoesNotCallService() {
    JwtAuthenticationToken auth = org.mockito.Mockito.mock(JwtAuthenticationToken.class);
    Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
    when(auth.getToken()).thenReturn(jwt);
    when(jwt.getSubject()).thenReturn(null);

    assertThatThrownBy(() -> controller.myEligibility(auth))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("subject");
    verifyNoInteractions(service);
  }

  @Test
  void myEligibility_whenAuthHasNullToken_throwsAccessDeniedAndDoesNotCallService() {
    JwtAuthenticationToken auth = org.mockito.Mockito.mock(JwtAuthenticationToken.class);
    when(auth.getToken()).thenReturn(null);

    assertThatThrownBy(() -> controller.myEligibility(auth))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("JWT");
    verifyNoInteractions(service);
  }

  // ── myEligibilityForRanks ───────────────────────────────────────────────

  @Test
  void myEligibilityForRanks_extractsSubAndForwardsRanks() {
    String sub = "bob-uuid";
    JwtAuthenticationToken auth = authWithSub(sub);
    PromotionEligibilityResponse expected = eligibility(sub, 20, 19);
    when(service.evaluateForRanks(sub, 20, 19)).thenReturn(expected);

    PromotionEligibilityResponse result = controller.myEligibilityForRanks(20, 19, auth);

    assertThat(result).isSameAs(expected);
    verify(service).evaluateForRanks(sub, 20, 19);
  }

  @Test
  void myEligibilityForRanks_whenAuthIsNull_throwsAccessDeniedAndDoesNotCallService() {
    assertThatThrownBy(() -> controller.myEligibilityForRanks(20, 19, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(service);
  }

  // ── eligibilityForUser (admin/officer view) ─────────────────────────────

  @Test
  void eligibilityForUser_forwardsTargetUserIdToService() {
    String targetUserId = "carol-uuid";
    List<PromotionEligibilityResponse> expected = List.of(eligibility(targetUserId, 20, 19));
    when(service.evaluateAllForUserAsAdmin(targetUserId)).thenReturn(expected);

    List<PromotionEligibilityResponse> result = controller.eligibilityForUser(targetUserId);

    assertThat(result).isSameAs(expected);
    verify(service).evaluateAllForUserAsAdmin(targetUserId);
  }
}
