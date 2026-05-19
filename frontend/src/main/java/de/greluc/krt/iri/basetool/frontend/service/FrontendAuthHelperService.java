package de.greluc.krt.iri.basetool.frontend.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Centralised access point for the Spring Security {@link SecurityContextHolder} on the frontend
 * side.
 *
 * <p>The frontend module renders Thymeleaf pages on behalf of an OAuth2 browser session rather than
 * processing a JWT-bearing API call, so it has no JWT {@code sub} the way the backend does; the
 * helper therefore exposes only the two predicates the rendering layer actually needs (is the
 * caller authenticated? do they reach the admin role?). New callers should depend on this bean
 * instead of touching {@link SecurityContextHolder} directly so the request-scoped auth contract
 * stays testable through a single, mock-friendly seam.
 *
 * <p>Existing frontend touch points that still call {@link SecurityContextHolder} directly
 * (filters, exception handler, page controllers that propagate the JWT to the backend) are
 * out-of-scope for the Phase-6 follow-up — those reach into security context for low-level concerns
 * (bearer-token relay, MDC logging) where the indirection would not pay for itself.
 */
@Service
public class FrontendAuthHelperService {

  /**
   * {@code true} if the current request carries an authenticated, non-anonymous principal.
   * Anonymous tokens and missing security contexts both yield {@code false}, matching the backend's
   * {@code AuthHelperService#isAuthenticated()} semantics.
   *
   * @return whether the current request is authenticated.
   */
  public boolean isAuthenticated() {
    Authentication auth = currentAuthentication();
    return auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken);
  }

  /**
   * {@code true} if the current authentication carries the {@code ROLE_ADMIN} authority directly.
   * The frontend does not configure a role hierarchy of its own — the bearer-token relay forwards
   * authorities verbatim — so this is a literal-match check rather than a reachability check.
   *
   * @return whether the current principal is an admin.
   */
  public boolean isAdmin() {
    Authentication auth = currentAuthentication();
    if (auth == null) {
      return false;
    }
    return auth.getAuthorities().stream().map(Object::toString).anyMatch("ROLE_ADMIN"::equals);
  }

  private Authentication currentAuthentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
