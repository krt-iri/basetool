package de.greluc.krt.iri.basetool.backend.logging;

import de.greluc.krt.iri.basetool.backend.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Central correlation / MDC enrichment filter.
 *
 * <p>Each request is decorated with two MDC keys:
 *
 * <ul>
 *   <li><b>correlationId</b> – either taken from the inbound header (configurable via {@link
 *       LoggingProperties#getCorrelationIdHeader()}) or freshly generated as UUID. The effective id
 *       is echoed back in the response header of the same name so clients/proxies can trace the
 *       same request end-to-end.
 *   <li><b>userId</b> – the JWT {@code sub} claim of the authenticated principal, or {@code
 *       anonymous} for unauthenticated traffic. Intentionally restricted to {@code sub} to avoid
 *       leaking PII (see AGENTS.md: no emails/names/tokens in logs).
 * </ul>
 *
 * <p>The MDC is cleared in a {@code finally} block to prevent bleed-through on pooled or virtual
 * threads. The filter runs after Spring Security (order {@link Ordered#LOWEST_PRECEDENCE} minus a
 * small delta) so that {@link JwtAuthenticationToken} is already populated when the MDC is set. A
 * secondary lightweight pass at filter start still generates / echoes the correlation id even for
 * unauthenticated requests, ensuring every log line has the same id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

  /** Maximum accepted length for an inbound correlation id to avoid abuse / log injection. */
  private static final int MAX_ID_LENGTH = 128;

  private static final String ANONYMOUS = "anonymous";

  private final LoggingProperties loggingProperties;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    final String correlationId = resolveCorrelationId(request);
    final String userId = resolveUserId();

    MDC.put(loggingProperties.getCorrelationIdMdcKey(), correlationId);
    MDC.put(loggingProperties.getUserIdMdcKey(), userId);
    response.setHeader(loggingProperties.getCorrelationIdHeader(), correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(loggingProperties.getCorrelationIdMdcKey());
      MDC.remove(loggingProperties.getUserIdMdcKey());
    }
  }

  @NotNull private String resolveCorrelationId(@NotNull HttpServletRequest request) {
    String inbound = request.getHeader(loggingProperties.getCorrelationIdHeader());
    if (inbound != null && !inbound.isBlank() && isSafe(inbound)) {
      return inbound.length() > MAX_ID_LENGTH ? inbound.substring(0, MAX_ID_LENGTH) : inbound;
    }
    return UUID.randomUUID().toString();
  }

  /**
   * Accept only characters that cannot break a log line or a response header. This is the same
   * practice Spring Cloud Sleuth / Micrometer Tracing apply to inbound B3 trace ids.
   */
  private static boolean isSafe(@NotNull String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean allowed =
          (c >= '0' && c <= '9')
              || (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || c == '-'
              || c == '_'
              || c == '.';
      if (!allowed) {
        return false;
      }
    }
    return true;
  }

  @NotNull private static String resolveUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return ANONYMOUS;
  }

  /**
   * Run very late in the servlet filter chain so Spring Security has already populated the {@link
   * SecurityContextHolder}. Using {@link Ordered#LOWEST_PRECEDENCE} minus a constant lets
   * downstream filters (e.g. request logging) still read the MDC values we set here.
   */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 100;
  }
}
