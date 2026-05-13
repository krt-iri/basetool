package de.greluc.krt.iri.basetool.backend.service;

import java.util.Collection;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centralised access point for the Spring Security {@link SecurityContextHolder}.
 *
 * <p>This service is the single seam through which controllers, mappers and non-{@code UserService}
 * business code may consult the currently authenticated principal. Touching {@link
 * SecurityContextHolder} directly from anywhere else is forbidden by an ArchUnit rule (see {@code
 * ArchitectureTest}) — the rationale is laid out in CLAUDE.md: the same business method must not
 * behave differently depending on which thread invokes it (scheduling, async, message listeners),
 * and concentrating the read here makes the contract testable without a full Spring security
 * context.
 *
 * <p>The helper deliberately exposes a narrow surface — just enough to cover the role-hierarchy
 * checks the controllers used to inline ({@link #isLogisticianOrAbove()} etc.) plus a small set of
 * generic primitives ({@link #currentAuthentication()}, {@link #isAuthenticated()}, {@link
 * #hasReachableRole(String)}).
 */
@Service
@RequiredArgsConstructor
public class AuthHelperService {

  private final RoleHierarchy roleHierarchy;

  /**
   * Returns the current {@link Authentication}, or empty if no security context is bound, the
   * context contains no authentication, or the authentication is an {@link
   * AnonymousAuthenticationToken}. Callers that explicitly need to inspect the anonymous principal
   * should use {@link #rawAuthentication()}.
   */
  @NotNull
  public Optional<Authentication> currentAuthentication() {
    Authentication auth = rawAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    return Optional.of(auth);
  }

  /**
   * Returns the current authentication including anonymous tokens. Useful for filter/mapper code
   * that wants to distinguish "no authentication at all" from "anonymous principal".
   */
  @Nullable
  public Authentication rawAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }

  /** {@code true} if the current request carries an authenticated, non-anonymous principal. */
  public boolean isAuthenticated() {
    Authentication auth = rawAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken);
  }

  /**
   * {@code true} if the current authentication can reach {@code role} via the configured {@link
   * RoleHierarchy}. Pass the role with the {@code ROLE_} prefix (e.g. {@code "ROLE_LOGISTICIAN"}).
   *
   * <p>"Reachable" means: either the principal has the role directly, or the role is implied by a
   * higher role in the hierarchy (e.g. {@code ROLE_ADMIN} reaches {@code ROLE_LOGISTICIAN} because
   * {@code SecurityConfig} declares {@code ROLE_ADMIN > ROLE_LOGISTICIAN}).
   */
  public boolean hasReachableRole(@NotNull String role) {
    Authentication auth = rawAuthentication();
    if (auth == null) {
      return false;
    }
    Collection<? extends GrantedAuthority> reachable =
        roleHierarchy.getReachableGrantedAuthorities(auth.getAuthorities());
    return reachable.stream().anyMatch(a -> role.equals(a.getAuthority()));
  }

  /**
   * Shortcut for {@link #hasReachableRole(String) hasReachableRole("ROLE_LOGISTICIAN")}. Because
   * the role hierarchy declares {@code ROLE_ADMIN > ROLE_LOGISTICIAN} and {@code ROLE_OFFICER >
   * ROLE_LOGISTICIAN}, this returns {@code true} for any of the three elevated roles.
   */
  public boolean isLogisticianOrAbove() {
    return hasReachableRole("ROLE_LOGISTICIAN");
  }
}
