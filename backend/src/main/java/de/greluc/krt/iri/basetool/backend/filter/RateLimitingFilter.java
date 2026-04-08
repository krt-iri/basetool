package de.greluc.krt.iri.basetool.backend.filter;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import de.greluc.krt.iri.basetool.backend.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final AppProblemProperties problemProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        List<String> patterns = properties.getPaths();
        if (patterns == null || patterns.isEmpty()) return true;
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        String pattern = firstMatchingPattern(request.getRequestURI(), properties.getPaths());
        String key = clientIp + "|" + (pattern != null ? pattern : "");
        Bucket bucket = cache.computeIfAbsent(key, k -> createNewBucket(properties.getCapacity(), properties.getRefillTokens(), properties.getRefillPeriod()));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getCapacity()));
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long nanosToWait = probe.getNanosToWaitForRefill();
            long secondsToWait = (long) Math.ceil(nanosToWait / 1_000_000_000.0);
            log.warn("Rate limit exceeded: ip={}, path={}, retryAfterSeconds={}", clientIp, request.getRequestURI(), secondsToWait);
            writeTooManyRequests(response, request, secondsToWait);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        List<String> trusted = properties.getTrustedProxies();
        
        boolean isTrusted = trusted != null && (trusted.contains("*") || trusted.contains(remoteAddr));
        
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
        if (patterns == null) return null;
        for (String p : patterns) {
            if (pathMatcher.match(p, path)) return p;
        }
        return null;
    }

    private Bucket createNewBucket(int capacity, int refillTokens, Duration period) {
        Bandwidth limit = Bandwidth.builder().capacity(capacity).refillGreedy(refillTokens, period).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getCapacity()));
        response.setHeader("X-Rate-Limit-Remaining", "0");
        response.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));

        String body = "{" +
                "\"type\":\"" + problemProperties.getBaseUri() + "rate-limit-exceeded\"," +
                "\"title\":\"Too Many Requests\"," +
                "\"status\":" + HttpStatus.TOO_MANY_REQUESTS.value() + "," +
                "\"detail\":\"Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.\"," +
                "\"instance\":\"" + request.getRequestURI() + "\"" +
                "}";
        response.getWriter().write(body);
    }
}
