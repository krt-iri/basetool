package de.greluc.krt.iri.basetool.backend.filter;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import de.greluc.krt.iri.basetool.backend.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP token-bucket rate limiter implemented with Bucket4j buckets in a Caffeine cache.
 *
 * <p>Active only on URI patterns listed in {@code app.rate-limit.paths} and disabled wholesale via
 * {@code app.rate-limit.enabled=false}. Buckets are keyed by {@code clientIp + "|" + pattern} so
 * each user-pattern combination gets its own token budget — a flood on {@code /api/v1/login} does
 * not consume the budget for {@code /api/v1/missions}. The Caffeine cache expires entries after one
 * hour of inactivity (1h hibernate-window keeps abusive clients limited across short pauses) and is
 * capped at 100 000 entries to bound memory under a Slowloris-style attack.
 *
 * <p>{@code X-Forwarded-For} is only honored when the immediate peer is listed in {@code
 * app.rate-limit.trusted-proxies}; blanket trust (the literal {@code "*"}) is explicitly rejected
 * because it would let any client spoof the header and get a fresh bucket per request. Rejected
 * requests get a 429 with an RFC&nbsp;7807 body and rate-limit headers ({@code X-Rate-Limit-Limit},
 * {@code X-Rate-Limit-Remaining}, {@code X-Rate-Limit-Retry-After-Seconds}).
 */
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

  private static final Duration BUCKET_EXPIRE_AFTER_ACCESS = Duration.ofHours(1);
  private static final long BUCKET_MAX_ENTRIES = 100_000L;

  private final RateLimitProperties properties;
  private final AppProblemProperties problemProperties;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private final Cache<String, Bucket> bucketCache;

  /**
   * Constructs the filter with the bucket cache sized from compile-time constants ({@code 1h}
   * idle-expiry, 100 000 entries max). Both properties classes carry the validated
   * {@code @ConfigurationProperties} values pulled from {@code application.yml}.
   *
   * @param properties bucket capacity/refill configuration plus path patterns and trusted proxies
   * @param problemProperties RFC&nbsp;7807 problem-type base URI used in the 429 response body
   */
  public RateLimitingFilter(
      RateLimitProperties properties, AppProblemProperties problemProperties) {
    this.properties = properties;
    this.problemProperties = problemProperties;
    this.bucketCache =
        Caffeine.newBuilder()
            .expireAfterAccess(BUCKET_EXPIRE_AFTER_ACCESS)
            .maximumSize(BUCKET_MAX_ENTRIES)
            .build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    List<String> patterns = properties.getPaths();
    if (patterns == null || patterns.isEmpty()) {
      return true;
    }
    for (String pattern : patterns) {
      if (pathMatcher.match(pattern, path)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String clientIp = resolveClientIp(request);
    String pattern = firstMatchingPattern(request.getRequestURI(), properties.getPaths());
    String key = clientIp + "|" + (pattern != null ? pattern : "");
    Bucket bucket =
        bucketCache.get(
            key,
            k ->
                createNewBucket(
                    properties.getCapacity(),
                    properties.getRefillTokens(),
                    properties.getRefillPeriod()));

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      response.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getCapacity()));
      response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
      filterChain.doFilter(request, response);
    } else {
      long nanosToWait = probe.getNanosToWaitForRefill();
      long secondsToWait = (long) Math.ceil(nanosToWait / 1_000_000_000.0);
      log.warn(
          "Rate limit exceeded: ip={}, path={}, retryAfterSeconds={}",
          clientIp,
          request.getRequestURI(),
          secondsToWait);
      writeTooManyRequests(response, request, secondsToWait);
    }
  }

  /**
   * Resolves the rate-limit key for the incoming request. {@code X-Forwarded-For} is only honored
   * when the immediate peer ({@code request.getRemoteAddr()}) is listed by exact match in {@code
   * app.rate-limit.trusted-proxies}; the literal {@code "*"} is intentionally NOT a valid trust
   * value, since blanket trust lets any client spoof the header and obtain a fresh bucket per
   * request.
   */
  private String resolveClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    List<String> trusted = properties.getTrustedProxies();

    boolean isTrusted =
        trusted != null
            && !trusted.isEmpty()
            && trusted.stream()
                .filter(p -> p != null && !"*".equals(p))
                .anyMatch(remoteAddr::equals);

    if (isTrusted) {
      String xff = request.getHeader("X-Forwarded-For");
      if (xff != null && !xff.isBlank()) {
        // First IP in the list is the original client
        int idx = xff.indexOf(',');
        return idx > 0 ? xff.substring(0, idx).trim() : xff.trim();
      }
    }

    return remoteAddr;
  }

  private String firstMatchingPattern(String path, List<String> patterns) {
    if (patterns == null) {
      return null;
    }
    for (String p : patterns) {
      if (pathMatcher.match(p, path)) {
        return p;
      }
    }
    return null;
  }

  private Bucket createNewBucket(int capacity, int refillTokens, Duration period) {
    Bandwidth limit =
        Bandwidth.builder().capacity(capacity).refillGreedy(refillTokens, period).build();
    return Bucket.builder().addLimit(limit).build();
  }

  private void writeTooManyRequests(
      HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getCapacity()));
    response.setHeader("X-Rate-Limit-Remaining", "0");
    response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

    // The {@code instance} field reflects the request URI, which is fully attacker-controlled.
    // String concatenation directly into a JSON literal would let a crafted path like
    // {@code /api/v1/x","fake":"injected} break out of the quoted value and append arbitrary
    // top-level fields, polluting the RFC 7807 contract a client may rely on (JSON-injection,
    // CodeQL: java/xss). Escape via Jackson's {@link JsonStringEncoder} so that {@code "},
    // {@code \}, control chars and unicode separators land as proper JSON escapes.
    JsonStringEncoder jsonEncoder = JsonStringEncoder.getInstance();
    String instanceEscaped = new String(jsonEncoder.quoteAsString(request.getRequestURI()));
    String body =
        "{"
            + "\"type\":\""
            + problemProperties.getBaseUri()
            + "rate-limit-exceeded\","
            + "\"title\":\"Too Many Requests\","
            + "\"status\":"
            + HttpStatus.TOO_MANY_REQUESTS.value()
            + ","
            + "\"detail\":\"Rate limit exceeded. Try again in "
            + retryAfterSeconds
            + " seconds.\","
            + "\"instance\":\""
            + instanceEscaped
            + "\""
            + "}";
    response.getWriter().write(body);
  }
}
