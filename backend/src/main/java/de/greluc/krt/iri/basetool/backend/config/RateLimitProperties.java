package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code app.rate-limit.*}.
 *
 * <p>Consumed by {@link de.greluc.krt.iri.basetool.backend.filter.RateLimitingFilter}. Capacity +
 * refill rate define the Bucket4j token bucket; {@code paths} narrows the filter to API endpoints
 * only; {@code trustedProxies} controls whether the filter honors {@code X-Forwarded-For} from a
 * reverse proxy. The defaults (300 tokens, refilled 300/min) are tuned for the project's typical
 * mission-planning workload — adjust per environment, not via global wildcards.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
  /** Enable/disable the rate limiter globally. */
  private boolean enabled = true;

  /** Ant-style path patterns to protect (e.g. /api/**) */
  @NotEmpty private List<String> paths = java.util.List.of("/api/**");

  /** Bucket capacity (max tokens). */
  @Min(1)
  private int capacity = 300;

  /** Tokens refilled per period. */
  @Min(1)
  private int refillTokens = 300;

  /** Refill period. */
  @NotNull private Duration refillPeriod = Duration.ofMinutes(1);

  /**
   * List of trusted reverse-proxy IPs, exact match against {@code request.getRemoteAddr()}. Only
   * when the immediate peer is in this list does the filter honor {@code X-Forwarded-For} to derive
   * the client IP for bucketing. An empty list disables {@code X-Forwarded-For} entirely. The
   * literal {@code "*"} is NOT a valid entry and is silently ignored - blanket trust would let any
   * client spoof the header and bypass IP-based rate limiting.
   */
  private List<String> trustedProxies = new java.util.ArrayList<>();
}
