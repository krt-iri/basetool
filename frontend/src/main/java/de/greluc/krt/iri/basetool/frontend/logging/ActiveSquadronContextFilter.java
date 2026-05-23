package de.greluc.krt.iri.basetool.frontend.logging;

import de.greluc.krt.iri.basetool.frontend.controller.MeFrontendController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that mirrors the admin's active-squadron selection from the frontend's Spring
 * Session into {@link ActiveSquadronContext} on every request and clears it on the way out.
 *
 * <p>The {@code ActiveSquadronRelayFilter} on the WebClient pipeline cannot read the session
 * directly because it runs on Netty reactor threads where {@code RequestContextHolder} is not
 * bound. A thread-local snapshot taken on the Tomcat request thread, combined with Reactor's
 * automatic context propagation (enabled by Spring Boot 4), survives the hop. The cleanup in the
 * {@code finally} block prevents bleed-through onto pooled or virtual threads.
 *
 * <p>The filter runs early in the chain (one notch after {@code CorrelationIdFilter}) so the value
 * is visible to every downstream component that issues a backend call.
 */
@Component
public class ActiveSquadronContextFilter extends OncePerRequestFilter implements Ordered {

  /**
   * Filter order: late enough that Spring Session's {@code SessionRepositoryFilter} (default order
   * {@code Integer.MIN_VALUE + 50}) has already wrapped the request with the Redis-backed session,
   * and that Spring Security has populated the auth context. Same precedence band as {@code
   * CorrelationIdFilter} so the active squadron is bound before any controller-emitted backend call
   * leaves the JVM.
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 99;
  }

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain chain)
      throws ServletException, IOException {
    UUID active = readActiveSquadron(request);
    if (active != null) {
      ActiveSquadronContext.set(active);
    }
    try {
      chain.doFilter(request, response);
    } finally {
      ActiveSquadronContext.clear();
    }
  }

  private UUID readActiveSquadron(@NotNull HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    // R5.e: read the new ACTIVE_ORG_UNIT_SESSION_KEY first; fall back to the legacy
    // ACTIVE_SQUADRON_SESSION_KEY so admin sessions stored under the old key during deploy
    // continue to honour the pin until the user's next switcher interaction (which rewrites
    // the new key). The legacy fallback comes out once the destructive cleanup release lands.
    UUID fromNew =
        ActiveSquadronContext.coerce(
            session.getAttribute(MeFrontendController.ACTIVE_ORG_UNIT_SESSION_KEY));
    if (fromNew != null) {
      return fromNew;
    }
    return ActiveSquadronContext.coerce(
        session.getAttribute(MeFrontendController.ACTIVE_SQUADRON_SESSION_KEY));
  }
}
