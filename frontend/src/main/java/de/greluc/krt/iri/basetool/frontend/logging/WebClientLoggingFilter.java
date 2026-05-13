package de.greluc.krt.iri.basetool.frontend.logging;

import de.greluc.krt.iri.basetool.frontend.config.LoggingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

/**
 * {@link ExchangeFilterFunction}s emitted by the frontend {@code WebClient} to achieve two goals:
 *
 * <ul>
 *   <li><b>Correlation propagation</b> – injects the current request's {@code X-Correlation-Id}
 *       (configurable via {@link LoggingProperties#getCorrelationIdHeader()}) into outbound backend
 *       calls so the backend log line shares the same id.
 *   <li><b>Structured call logging</b> – logs one line per outbound call with method, host, path,
 *       status and elapsed time. Slow calls (above {@link
 *       LoggingProperties#getSlowBackendCallThresholdMs()}) escalate to WARN so they can be flagged
 *       in dashboards. Network-level failures are logged at WARN as well, with the exception class
 *       and message only (no stack trace – the exception is re-thrown and the frontend {@code
 *       GlobalExceptionHandler} decides on user-facing behaviour).
 * </ul>
 *
 * <p>Query strings are intentionally excluded from the log line because they may carry PII such as
 * filter expressions containing email fragments or user ids of other users (see AGENTS.md: no
 * information disclosure).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientLoggingFilter {

  private final LoggingProperties loggingProperties;

  /**
   * @return filter that adds the correlation id header if one is bound to the current thread.
   */
  @NotNull public ExchangeFilterFunction correlationIdPropagation() {
    return (request, next) -> {
      String correlationId = CorrelationContext.get();
      if (correlationId == null || correlationId.isBlank()) {
        return next.exchange(request);
      }
      ClientRequest propagated =
          ClientRequest.from(request)
              .header(loggingProperties.getCorrelationIdHeader(), correlationId)
              .build();
      return next.exchange(propagated);
    };
  }

  /**
   * @return filter that logs one line per call including method/host/path/status/duration. Slow
   *     calls are escalated to WARN.
   */
  @NotNull public ExchangeFilterFunction callLogging() {
    return (request, next) -> {
      final long start = System.nanoTime();
      final String method = request.method().name();
      final String host = request.url().getHost();
      final String path = request.url().getPath();
      return next.exchange(request)
          .doOnNext(response -> logCall(method, host, path, response.statusCode().value(), start))
          .doOnError(err -> logError(method, host, path, err, start));
    };
  }

  private void logCall(
      @NotNull String method,
      @NotNull String host,
      @NotNull String path,
      int status,
      long startNanos) {
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
    if (durationMs >= loggingProperties.getSlowBackendCallThresholdMs() || status >= 500) {
      log.warn("Backend call {} {}{} -> {} in {} ms", method, host, path, status, durationMs);
    } else if (log.isInfoEnabled()) {
      log.info("Backend call {} {}{} -> {} in {} ms", method, host, path, status, durationMs);
    }
  }

  private void logError(
      @NotNull String method,
      @NotNull String host,
      @NotNull String path,
      @NotNull Throwable err,
      long startNanos) {
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
    log.warn(
        "Backend call {} {}{} failed after {} ms: {}: {}",
        method,
        host,
        path,
        durationMs,
        err.getClass().getSimpleName(),
        err.getMessage());
  }
}
