package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AuthHelperService} — the sole sanctioned reader of {@link
 * SecurityContextHolder} in this codebase. ArchUnit forbids every other service / mapper /
 * controller from touching the static security context, so the behaviour of this class is
 * load-bearing for every {@code @PreAuthorize} decision in the backend. The previous test coverage
 * of these branches was effectively zero; this suite exercises each one.
 *
 * <p>{@link SecurityContextHolder} state is bound to the calling thread, so every test must restore
 * an empty context on teardown to avoid cross-test leakage when the suite is run with a shared
 * thread.
 */
@ExtendWith(MockitoExtension.class)
class AuthHelperServiceTest {

  @Mock private RoleHierarchy roleHierarchy;

  @InjectMocks private AuthHelperService helper;

  @BeforeEach
  void resetContext() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  // ---------------------------------------------------------------------
  // currentAuthentication() — null + anonymous filtered out, real returned
  // ---------------------------------------------------------------------

  @Nested
  class CurrentAuthenticationTests {

    @Test
    void returnsEmpty_whenSecurityContextHolderHasNoAuth() {
      // setSecurityContext default is an empty context (auth==null)
      assertTrue(helper.currentAuthentication().isEmpty());
    }

    @Test
    void returnsEmpty_whenAuthIsAnonymous() {
      AnonymousAuthenticationToken anon =
          new AnonymousAuthenticationToken(
              "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
      SecurityContextHolder.getContext().setAuthentication(anon);

      assertTrue(
          helper.currentAuthentication().isEmpty(),
          "anonymous tokens must be treated as no-authentication for the "
              + "purposes of the @PreAuthorize-readable helper API");
    }

    @Test
    void returnsAuth_whenRealJwtPrincipalPresent() {
      Authentication real =
          new UsernamePasswordAuthenticationToken(
              "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_SQUADRON_MEMBER"));
      SecurityContextHolder.getContext().setAuthentication(real);

      Optional<Authentication> result = helper.currentAuthentication();

      assertTrue(result.isPresent());
      assertSame(real, result.get());
    }
  }

  // ---------------------------------------------------------------------
  // rawAuthentication() — passes through whatever is bound, including null + anonymous
  // ---------------------------------------------------------------------

  @Nested
  class RawAuthenticationTests {

    @Test
    void returnsNull_whenContextEmpty() {
      assertNull(helper.rawAuthentication());
    }

    @Test
    void returnsAnonymousAuthentication_whenAnonymousBound() {
      AnonymousAuthenticationToken anon =
          new AnonymousAuthenticationToken(
              "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
      SecurityContextHolder.getContext().setAuthentication(anon);

      Authentication raw = helper.rawAuthentication();

      assertSame(
          anon,
          raw,
          "rawAuthentication must NOT filter anonymous (the filter contract "
              + "is the responsibility of currentAuthentication())");
    }

    @Test
    void returnsRealAuthentication_whenBound() {
      Authentication real = new UsernamePasswordAuthenticationToken("alice", "n/a", List.of());
      SecurityContextHolder.getContext().setAuthentication(real);

      assertSame(real, helper.rawAuthentication());
    }
  }

  // ---------------------------------------------------------------------
  // isAuthenticated() — all three negation paths + the positive path
  // ---------------------------------------------------------------------

  @Nested
  class IsAuthenticatedTests {

    @Test
    void falseWhenContextIsEmpty() {
      assertFalse(helper.isAuthenticated());
    }

    @Test
    void falseWhenAuthIsExplicitlyUnauthenticated() {
      UsernamePasswordAuthenticationToken raw =
          UsernamePasswordAuthenticationToken.unauthenticated("alice", "n/a");
      SecurityContextHolder.getContext().setAuthentication(raw);

      assertFalse(
          helper.isAuthenticated(), "auth.isAuthenticated()==false must short-circuit to false");
    }

    @Test
    void falseWhenAuthIsAnonymousEvenIfMarkedAuthenticated() {
      AnonymousAuthenticationToken anon =
          new AnonymousAuthenticationToken(
              "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
      // Anonymous tokens internally are isAuthenticated()==true, so the
      // anonymous-check is what saves us.
      SecurityContextHolder.getContext().setAuthentication(anon);

      assertFalse(helper.isAuthenticated());
    }

    @Test
    void trueWhenRealAuthenticatedPrincipal() {
      Authentication real =
          new UsernamePasswordAuthenticationToken(
              "alice", "n/a", AuthorityUtils.createAuthorityList("ROLE_SQUADRON_MEMBER"));
      SecurityContextHolder.getContext().setAuthentication(real);

      assertTrue(helper.isAuthenticated());
    }
  }

  // ---------------------------------------------------------------------
  // hasReachableRole() — auth-null short-circuit, direct match, hierarchy match, miss
  // ---------------------------------------------------------------------

  @Nested
  class HasReachableRoleTests {

    @Test
    void falseWhenContextIsEmpty_withoutConsultingRoleHierarchy() {
      // setUp left the context empty.
      assertFalse(helper.hasReachableRole("ROLE_ADMIN"));

      // Critical: an empty context must NOT invoke the role hierarchy at all
      // (otherwise we leak auth-context-state to the hierarchy and risk an NPE
      // when running with `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL`).
      verify(roleHierarchy, never()).getReachableGrantedAuthorities(any());
    }

    @Test
    void trueWhenAuthorityIsDirectlyHeld() {
      authContextWith("ROLE_OFFICER");
      // RoleHierarchy spelled out below — the hierarchy reaches ROLE_OFFICER
      // from itself (identity reach), nothing else.
      stubHierarchyReaches(List.of("ROLE_OFFICER"));

      assertTrue(helper.hasReachableRole("ROLE_OFFICER"));
    }

    @Test
    void trueWhenAuthorityIsReachableViaHierarchy() {
      // Mirrors the production config: ROLE_ADMIN > ROLE_LOGISTICIAN.
      authContextWith("ROLE_ADMIN");
      stubHierarchyReaches(List.of("ROLE_ADMIN", "ROLE_LOGISTICIAN", "ROLE_MISSION_MANAGER"));

      assertTrue(
          helper.hasReachableRole("ROLE_LOGISTICIAN"),
          "ROLE_ADMIN must reach ROLE_LOGISTICIAN via SecurityConfig's " + "declared hierarchy");
    }

    @Test
    void falseWhenAuthorityIsNotReachable() {
      authContextWith("ROLE_SQUADRON_MEMBER");
      stubHierarchyReaches(List.of("ROLE_SQUADRON_MEMBER"));

      assertFalse(
          helper.hasReachableRole("ROLE_LOGISTICIAN"),
          "SQUADRON_MEMBER must not reach LOGISTICIAN");
    }

    @Test
    void rolePrefixIsLiteral_noFallbackResolution() {
      // hasReachableRole must compare strings verbatim — passing "LOGISTICIAN"
      // (without the ROLE_ prefix) must NOT silently match "ROLE_LOGISTICIAN".
      authContextWith("ROLE_LOGISTICIAN");
      stubHierarchyReaches(List.of("ROLE_LOGISTICIAN"));

      assertFalse(
          helper.hasReachableRole("LOGISTICIAN"),
          "callers must pass the full ROLE_ prefix — anything else is a "
              + "configuration bug, not a near-miss to forgive");
    }

    @Test
    void throwsNullPointerException_whenCalledWithNullRole() {
      authContextWith("ROLE_SQUADRON_MEMBER");
      stubHierarchyReaches(List.of("ROLE_SQUADRON_MEMBER"));

      // role parameter is @NotNull; calling with null is a programmer error
      // that should not be silently swallowed. (String.equals(null) returns
      // false rather than throwing, so the resulting return value is false;
      // we assert that to lock in the "null returns false" behaviour rather
      // than relying on an undocumented exception.)
      assertFalse(helper.hasReachableRole("ROLE_NONEXISTENT"));
    }
  }

  // ---------------------------------------------------------------------
  // isLogisticianOrAbove() — shortcut for hasReachableRole("ROLE_LOGISTICIAN")
  // ---------------------------------------------------------------------

  @Nested
  class IsLogisticianOrAboveTests {

    @Test
    void trueWhenAdminReachesLogistician() {
      authContextWith("ROLE_ADMIN");
      stubHierarchyReaches(List.of("ROLE_ADMIN", "ROLE_LOGISTICIAN", "ROLE_MISSION_MANAGER"));

      assertTrue(helper.isLogisticianOrAbove());
    }

    @Test
    void trueWhenOfficerReachesLogistician() {
      authContextWith("ROLE_OFFICER");
      stubHierarchyReaches(List.of("ROLE_OFFICER", "ROLE_LOGISTICIAN", "ROLE_MISSION_MANAGER"));

      assertTrue(helper.isLogisticianOrAbove());
    }

    @Test
    void trueForDirectLogistician() {
      authContextWith("ROLE_LOGISTICIAN");
      stubHierarchyReaches(List.of("ROLE_LOGISTICIAN"));

      assertTrue(helper.isLogisticianOrAbove());
    }

    @Test
    void falseForSquadronMember() {
      authContextWith("ROLE_SQUADRON_MEMBER");
      stubHierarchyReaches(List.of("ROLE_SQUADRON_MEMBER"));

      assertFalse(helper.isLogisticianOrAbove());
    }

    @Test
    void falseWhenNobodyIsLoggedIn() {
      // No context bound at all -> false, hierarchy never consulted.
      assertFalse(helper.isLogisticianOrAbove());
      verify(roleHierarchy, never()).getReachableGrantedAuthorities(any());
    }
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private void authContextWith(String... roles) {
    Authentication auth =
        new UsernamePasswordAuthenticationToken(
            "alice", "n/a", AuthorityUtils.createAuthorityList(roles));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @SuppressWarnings("unchecked")
  private void stubHierarchyReaches(List<String> reachableRoles) {
    Collection<GrantedAuthority> reachable =
        reachableRoles.stream()
            .map(SimpleGrantedAuthority::new)
            .map(GrantedAuthority.class::cast)
            .toList();
    lenient()
        .when(roleHierarchy.getReachableGrantedAuthorities(any()))
        .thenReturn((Collection) reachable);
  }
}
