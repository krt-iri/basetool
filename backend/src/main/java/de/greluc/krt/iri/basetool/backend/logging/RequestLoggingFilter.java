package de.greluc.krt.iri.basetool.backend.logging;

import de.greluc.krt.iri.basetool.backend.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a single structured access-log line per request on INFO (or WARN for slow requests).
 *
 * <p>The line includes HTTP method, path, response status and elapsed duration. Correlation id and
 * user id are intentionally not duplicated into the message body because they are already rendered
 * via the MDC pattern in {@code logback-spring.xml}. This avoids noisy, redundant log output and
 * keeps structured-JSON fields clean.
 *
 * <p>Slow requests (duration above {@link LoggingProperties#getSlowRequestThresholdMs()}) are
 * logged at WARN so operators can flag them in dashboards / alerting.
 *
 * <p>Runs slightly earlier than {@link CorrelationIdFilter} in terms of filter order but since both
 * filters run once per request, the MDC values set by the correlation filter are still available
 * when this filter logs in its {@code finally} block – the correlation filter wraps this one via
 * the servlet chain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {

  private final LoggingProperties loggingProperties;

  @Override
  protected void doFilterInternal(
      @NotNull HttpServletRequest request,
      @NotNull HttpServletResponse response,
      @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - start) / 1_000_000L;
      int status = response.getStatus();
      String method = request.getMethod();
      String path = request.getRequestURI();
      if (durationMs >= loggingProperties.getSlowRequestThresholdMs()) {
        log.warn("Slow request {} {} -> {} in {} ms", method, path, status, durationMs);
      } else if (log.isInfoEnabled()) {
        log.info("{} {} -> {} in {} ms", method, path, status, durationMs);
      }
    }
  }

  /**
   * Skip static resources and the actuator to keep the request log focused on real business
   * traffic. Swagger-UI assets would otherwise dominate the log during development.
   */
  @Override
  protected boolean shouldNotFilter(@NotNull HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri.startsWith("/actuator/")
        || uri.startsWith("/swagger-ui")
        || uri.startsWith("/v3/api-docs")
        || uri.startsWith("/webjars/")
        || uri.equals("/favicon.ico");
  }

  /** Run just inside the correlation filter so the MDC is populated when we log. */
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 50;
  }
}
