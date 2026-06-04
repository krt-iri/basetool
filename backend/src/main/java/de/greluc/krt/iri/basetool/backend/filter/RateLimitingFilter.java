/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.filter;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.io.JsonStringEncoder;

/**
 * Per-IP token-bucket rate limiter implemented with Bucket4j buckets in a Caffeine cache.
 *
 * <p>Active only on URI patterns listed in {@code app.rate-limit.paths} and disabled wholesale via
 * {@code app.rate-limit.enabled=false}. Buckets are keyed by {@code clientIp + "|" + slot} where
 * {@code slot} is either {@code "path:<pattern>"} for the global default or {@code "rule:<name>"}
 * for an endpoint-specific rule (audit finding L-5, 2026-05-20). The Caffeine cache expires entries
 * after one hour of inactivity (1h hibernate-window keeps abusive clients limited across short
 * pauses) and is capped at 100 000 entries to bound memory under a Slowloris-style attack.
 *
 * <p>When both the global default and one or more endpoint-specific rules match, the filter
 * iterates them tightest-first and aborts on the first depleted bucket — so spam against the
 * anonymous-reachable POST endpoints (mission create, joborder create, finance-entry create,
 * participant CRUD) trips its per-endpoint budget before the loose global budget is touched. The
 * {@code X-Rate-Limit-Limit} / {@code X-Rate-Limit-Remaining} response headers reflect the tightest
 * matching budget on the happy path.
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
  private final List<IpAddressMatcher> trustedProxyMatchers;

  /**
   * Constructs the filter with the bucket cache sized from compile-time constants ({@code 1h}
   * idle-expiry, 100 000 entries max). Both properties classes carry the validated
   * {@code @ConfigurationProperties} values pulled from {@code application.yml}. The trusted-proxy
   * list is compiled into {@link IpAddressMatcher} instances once during construction so that the
   * per-request {@link #resolveClientIp} hot path no longer allocates a fresh matcher (and
   * re-parses the CIDR / IP literal) on every call. Malformed entries are logged once here and
   * dropped from the cached list, exactly as the previous per-request path did.
   *
   * @param properties bucket capacity/refill configuration plus path patterns, endpoint-specific
   *     rules and trusted proxies
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
    this.trustedProxyMatchers = compileTrustedProxies(properties.getTrustedProxies());
  }

  /**
   * Compiles each entry of the trusted-proxies list into an {@link IpAddressMatcher} once at filter
   * construction. Blank entries and the literal {@code "*"} blanket-trust sentinel are explicitly
   * excluded (the latter would let any client spoof {@code X-Forwarded-For}); malformed CIDR / IP
   * literals are logged at WARN and dropped so a configuration typo cannot disable the rest of the
   * trusted list. Returns an unmodifiable list because the field is shared across request threads.
   */
  private static List<IpAddressMatcher> compileTrustedProxies(List<String> entries) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    List<IpAddressMatcher> matchers = new ArrayList<>(entries.size());
    for (String entry : entries) {
      if (entry == null || entry.isBlank() || "*".equals(entry)) {
        continue;
      }
      try {
        matchers.add(new IpAddressMatcher(entry));
      } catch (IllegalArgumentException ex) {
        log.warn(
            "Invalid app.rate-limit.trusted-proxies entry '{}'; ignoring. Reason: {}",
            entry,
            ex.getMessage());
      }
    }
    return Collections.unmodifiableList(matchers);
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
    List<BucketSlot> slots = resolveSlots(request);
    if (slots.isEmpty()) {
      // The umbrella {@code paths} match was confirmed by {@link #shouldNotFilter}; if no slots
      // survive the rule walk that means none of the configured limits apply to this request
      // (e.g. a future config that lists rules but no global path-pattern). Pass through.
      filterChain.doFilter(request, response);
      return;
    }

    // Tightest first: a spam attack on an anonymous endpoint trips the per-endpoint budget before
    // the loose global budget is debited. Tie-break on slot key so the iteration order is
    // deterministic when two rules share a capacity — important for the 429-header attribution.
    slots.sort(Comparator.comparingInt(BucketSlot::capacity).thenComparing(BucketSlot::key));

    int tightestLimit = Integer.MAX_VALUE;
    long tightestRemaining = Long.MAX_VALUE;
    for (BucketSlot slot : slots) {
      Bucket bucket = bucketCache.get(clientIp + "|" + slot.key(), k -> createNewBucket(slot));
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      if (!probe.isConsumed()) {
        long nanosToWait = probe.getNanosToWaitForRefill();
        long secondsToWait = (long) Math.ceil(nanosToWait / 1_000_000_000.0);
        log.warn(
            "Rate limit exceeded: ip={}, slot={}, path={}, retryAfterSeconds={}",
            clientIp,
            slot.key(),
            request.getRequestURI(),
            secondsToWait);
        writeTooManyRequests(response, request, slot.capacity(), secondsToWait);
        return;
      }
      if (slot.capacity() < tightestLimit) {
        tightestLimit = slot.capacity();
        tightestRemaining = probe.getRemainingTokens();
      }
    }

    response.setHeader("X-Rate-Limit-Limit", String.valueOf(tightestLimit));
    response.setHeader("X-Rate-Limit-Remaining", String.valueOf(tightestRemaining));
    filterChain.doFilter(request, response);
  }

  /**
   * Builds the list of bucket slots that apply to the current request — the global default (when
   * the path matches {@link RateLimitProperties#getPaths()}) plus every endpoint-specific rule that
   * matches both the request URI AND the HTTP method. Order is irrelevant here; the caller sorts
   * tightest-first before consumption.
   */
  private List<BucketSlot> resolveSlots(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    List<BucketSlot> slots = new ArrayList<>();

    String globalPattern = firstMatchingPattern(path, properties.getPaths());
    if (globalPattern != null) {
      slots.add(
          new BucketSlot(
              "path:" + globalPattern,
              properties.getCapacity(),
              properties.getRefillTokens(),
              properties.getRefillPeriod()));
    }

    List<RateLimitProperties.Rule> rules = properties.getRules();
    if (rules != null) {
      for (RateLimitProperties.Rule rule : rules) {
        if (matchesMethod(rule.getMethods(), method) && matchesAnyPath(rule.getPaths(), path)) {
          slots.add(
              new BucketSlot(
                  "rule:" + rule.getName(),
                  rule.getCapacity(),
                  rule.getRefillTokens(),
                  rule.getRefillPeriod()));
        }
      }
    }

    return slots;
  }

  /**
   * {@code true} when the rule's HTTP-method allowlist is empty (any method allowed) or contains
   * the request's method (case-insensitive). Mirrors how Spring's {@code @RequestMapping(method =
   * …)} treats an empty array.
   */
  private static boolean matchesMethod(List<String> ruleMethods, String requestMethod) {
    if (ruleMethods == null || ruleMethods.isEmpty()) {
      return true;
    }
    for (String m : ruleMethods) {
      if (m != null && m.equalsIgnoreCase(requestMethod)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesAnyPath(List<String> patterns, String path) {
    if (patterns == null) {
      return false;
    }
    for (String p : patterns) {
      if (pathMatcher.match(p, path)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves the rate-limit key for the incoming request. {@code X-Forwarded-For} is only honored
   * when the immediate peer ({@code request.getRemoteAddr()}) matches one of the trusted-proxy
   * matchers compiled at filter construction; the literal {@code "*"} is intentionally NOT a valid
   * trust value, since blanket trust lets any client spoof the header and obtain a fresh bucket per
   * request.
   *
   * <p>Audit finding H-8: each trusted-proxies entry may be either an exact IP ({@code 172.17.0.1})
   * or a CIDR range ({@code 172.17.0.0/16}). Matching delegates to Spring Security's {@link
   * IpAddressMatcher} so docker-network ranges (typically {@code 172.17.0.0/16}, {@code
   * 10.0.0.0/8}, etc.) can be configured without enumerating every proxy IP individually.
   */
  private String resolveClientIp(HttpServletRequest request) {
    String remoteAddr = request.getRemoteAddr();
    boolean isTrusted = false;
    for (IpAddressMatcher matcher : trustedProxyMatchers) {
      if (matcher.matches(remoteAddr)) {
        isTrusted = true;
        break;
      }
    }

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

  private Bucket createNewBucket(BucketSlot slot) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(slot.capacity())
            .refillGreedy(slot.refillTokens(), slot.refillPeriod())
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private void writeTooManyRequests(
      HttpServletResponse response,
      HttpServletRequest request,
      int rejectedLimit,
      long retryAfterSeconds)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("X-Rate-Limit-Limit", String.valueOf(rejectedLimit));
    response.setHeader("X-Rate-Limit-Remaining", "0");
    response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

    // The {@code instance} field reflects the request URI, which is fully attacker-controlled.
    // String concatenation directly into a JSON literal would let a crafted path like
    // {@code /api/v1/x","fake":"injected} break out of the quoted value and append arbitrary
    // top-level fields, polluting the RFC 7807 contract a client may rely on (JSON-injection,
    // CodeQL: java/xss). Escape via Jackson's {@link JsonStringEncoder} so that {@code "},
    // {@code \}, control chars and unicode separators land as proper JSON escapes.
    // Jackson 3 dropped the String -> char[] overload; quote into a StringBuilder instead.
    JsonStringEncoder jsonEncoder = JsonStringEncoder.getInstance();
    StringBuilder instanceEscapedBuilder = new StringBuilder();
    jsonEncoder.quoteAsString(request.getRequestURI(), instanceEscapedBuilder);
    String instanceEscaped = instanceEscapedBuilder.toString();
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

  /**
   * Per-request snapshot of one rate-limit slot — the data needed to look up or create the
   * corresponding Bucket4j bucket. Carries the bucket key suffix, the bandwidth parameters and
   * nothing else; the surrounding filter sorts {@link #capacity()} ascending so the tightest budget
   * is consumed first.
   *
   * @param key bucket-key suffix combined with the client IP (e.g. {@code rule:mission-create})
   * @param capacity max tokens for this slot
   * @param refillTokens tokens added per {@link #refillPeriod()}
   * @param refillPeriod time window for the {@link #refillTokens()} refill
   */
  private record BucketSlot(String key, int capacity, int refillTokens, Duration refillPeriod) {}
}
