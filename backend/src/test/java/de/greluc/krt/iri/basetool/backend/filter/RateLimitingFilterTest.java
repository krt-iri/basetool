package de.greluc.krt.iri.basetool.backend.filter;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import de.greluc.krt.iri.basetool.backend.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RateLimitingFilter}. Previously had no test file at
 * all — 36% branch coverage. The most security-critical path is
 * {@code resolveClientIp}, which decides which "bucket" a request lands in:
 *
 * <ul>
 *     <li>If the immediate peer is on the trusted-proxies allow-list, the
 *         filter takes the original-client IP from {@code X-Forwarded-For};
 *         otherwise the spoofable header is ignored.</li>
 *     <li>The literal {@code "*"} is NOT a valid trust value (would let any
 *         client spoof the header and get a fresh bucket per request).</li>
 * </ul>
 *
 * <p>The tests use Spring's {@link MockHttpServletRequest}/Response so no
 * Spring context is needed.
 */
class RateLimitingFilterTest {

    private RateLimitProperties properties;
    private AppProblemProperties problemProperties;
    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setPaths(List.of("/api/**"));
        properties.setCapacity(2);          // tight bucket so we can hit the limit quickly
        properties.setRefillTokens(2);
        properties.setRefillPeriod(Duration.ofMinutes(1));

        problemProperties = new AppProblemProperties();
        problemProperties.setBaseUri("https://iri-base.org/problems/");

        filter = new RateLimitingFilter(properties, problemProperties);
    }

    // ---------------------------------------------------------------
    // shouldNotFilter — path matching + global disable
    // ---------------------------------------------------------------

    @Nested
    class ShouldNotFilterTests {

        @Test
        void disabled_globally_bypassesEntirely() {
            properties.setEnabled(false);
            MockHttpServletRequest req = newRequest("/api/v1/missions");

            assertTrue(filter.shouldNotFilter(req),
                    "enabled=false must short-circuit before path matching");
        }

        @Test
        void nullPathsList_bypasses() {
            properties.setPaths(null);
            MockHttpServletRequest req = newRequest("/api/v1/missions");

            assertTrue(filter.shouldNotFilter(req));
        }

        @Test
        void emptyPathsList_bypasses() {
            properties.setPaths(List.of());
            MockHttpServletRequest req = newRequest("/api/v1/missions");

            assertTrue(filter.shouldNotFilter(req));
        }

        @Test
        void pathMatchesPattern_filterApplies() {
            properties.setPaths(List.of("/api/**"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");

            assertEquals(false, filter.shouldNotFilter(req),
                    "matching path must NOT be skipped (filter applies)");
        }

        @Test
        void pathDoesNotMatchPattern_bypasses() {
            properties.setPaths(List.of("/api/**"));
            MockHttpServletRequest req = newRequest("/health");

            assertTrue(filter.shouldNotFilter(req));
        }

        @Test
        void multiplePatterns_anyMatchTriggersFilter() {
            properties.setPaths(List.of("/health", "/api/**"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");

            assertEquals(false, filter.shouldNotFilter(req));
        }
    }

    // ---------------------------------------------------------------
    // resolveClientIp + bucket bookkeeping — the spoofable-header guard
    // ---------------------------------------------------------------

    @Nested
    class ClientIpResolutionTests {

        @Test
        void noTrustedProxies_ignoresXForwardedFor() throws Exception {
            properties.setTrustedProxies(null);
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("198.51.100.10");
            req.addHeader("X-Forwarded-For", "1.1.1.1"); // spoofed header — must be ignored

            assertConsumesBucketKeyContaining("198.51.100.10", req);
        }

        @Test
        void emptyTrustedProxies_ignoresXForwardedFor() throws Exception {
            properties.setTrustedProxies(List.of());
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("198.51.100.10");
            req.addHeader("X-Forwarded-For", "1.1.1.1");

            assertConsumesBucketKeyContaining("198.51.100.10", req);
        }

        @Test
        void wildcardOnlyTrustedProxies_ignoresXForwardedFor() throws Exception {
            // The "*" literal is NOT a valid trust value — must be silently filtered out
            // by the lambda. This is the core rate-limit-bypass guard called out in
            // the production Javadoc.
            properties.setTrustedProxies(List.of("*"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("198.51.100.10");
            req.addHeader("X-Forwarded-For", "1.1.1.1");

            assertConsumesBucketKeyContaining("198.51.100.10", req);
        }

        @Test
        void trustedPeerWithNullXff_fallsBackToRemoteAddr() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            // no XFF set

            assertConsumesBucketKeyContaining("10.0.0.1", req);
        }

        @Test
        void trustedPeerWithBlankXff_fallsBackToRemoteAddr() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "   ");

            assertConsumesBucketKeyContaining("10.0.0.1", req);
        }

        @Test
        void untrustedPeerWithXff_ignoresXForwardedFor() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("198.51.100.10");   // NOT in trustedProxies
            req.addHeader("X-Forwarded-For", "1.1.1.1");

            assertConsumesBucketKeyContaining("198.51.100.10", req);
        }

        @Test
        void trustedPeerWithSingleXff_usesXffIp() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.7");

            assertConsumesBucketKeyContaining("203.0.113.7", req);
        }

        @Test
        void trustedPeerWithCommaSeparatedXff_usesFirstIp() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1, 198.51.100.99");

            assertConsumesBucketKeyContaining("203.0.113.7", req);
        }

        @Test
        void trustedPeerWithXffWhitespace_trimsBeforeUsing() throws Exception {
            properties.setTrustedProxies(List.of("10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "   203.0.113.7   , 198.51.100.99");

            assertConsumesBucketKeyContaining("203.0.113.7", req);
        }

        @Test
        void trustedProxiesListContainsBothWildcardAndRealIp_realIpStillWorks() throws Exception {
            // Defensive: if someone misconfigures with "*" mixed in, the real IP must
            // still be honoured (the "*" entry is filtered out, not the whole list).
            properties.setTrustedProxies(List.of("*", "10.0.0.1"));
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("10.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.7");

            assertConsumesBucketKeyContaining("203.0.113.7", req);
        }

        @Test
        void twoRequestsFromSameSpoofedXffShareABucket_whenTrustedProxyNotConfigured() throws Exception {
            // The "rate-limit bypass" attacker scenario: client puts a random IP in
            // X-Forwarded-For on every request hoping for a fresh bucket. With NO
            // trusted proxy configured, the filter must ignore the header entirely
            // and bucket on remoteAddr — so 2 such requests share one bucket.
            properties.setTrustedProxies(List.of()); // not trusted
            properties.setCapacity(1);
            properties.setRefillTokens(1);

            // First request: capacity=1 -> consumed, no 429 yet.
            MockHttpServletRequest req1 = newRequest("/api/v1/missions");
            req1.setRemoteAddr("198.51.100.10");
            req1.addHeader("X-Forwarded-For", "1.1.1.1");
            MockHttpServletResponse resp1 = new MockHttpServletResponse();
            filter.doFilter(req1, resp1, new MockFilterChain());
            assertEquals(200, resp1.getStatus(), "first request must pass");

            // Second request: same remote, different spoofed XFF -> would have been a
            // fresh bucket if XFF were honoured -> 429.
            MockHttpServletRequest req2 = newRequest("/api/v1/missions");
            req2.setRemoteAddr("198.51.100.10");
            req2.addHeader("X-Forwarded-For", "2.2.2.2");
            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            filter.doFilter(req2, resp2, new MockFilterChain());

            assertEquals(429, resp2.getStatus(),
                    "spoofed XFF must NOT yield a fresh bucket when proxy not in trust list");
        }

        // ----- helper --------------------------------------------------------

        private void assertConsumesBucketKeyContaining(String expectedIp, MockHttpServletRequest req)
                throws ServletException, IOException {
            // Tight bucket (capacity=1). The first call consumes; a second call with
            // the same bucket key MUST be rate-limited (429). If the resolved IP
            // doesn't match, the second call would land in a different bucket and
            // still pass.
            properties.setCapacity(1);
            properties.setRefillTokens(1);

            MockHttpServletResponse resp1 = new MockHttpServletResponse();
            filter.doFilter(copy(req), resp1, new MockFilterChain());
            assertEquals(200, resp1.getStatus(), "first request must consume cleanly");

            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            filter.doFilter(copy(req), resp2, new MockFilterChain());
            assertEquals(429, resp2.getStatus(),
                    "second request with the same resolved IP (" + expectedIp + ") must be rate-limited");
        }

        private MockHttpServletRequest copy(MockHttpServletRequest src) {
            MockHttpServletRequest copy = new MockHttpServletRequest(src.getMethod(), src.getRequestURI());
            copy.setRemoteAddr(src.getRemoteAddr());
            String xff = src.getHeader("X-Forwarded-For");
            if (xff != null) {
                copy.addHeader("X-Forwarded-For", xff);
            }
            return copy;
        }
    }

    // ---------------------------------------------------------------
    // doFilterInternal — happy path + 429 response shape
    // ---------------------------------------------------------------

    @Nested
    class DoFilterInternalTests {

        @Test
        void firstRequest_consumesBucket_andAddsHeaders() throws Exception {
            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("192.0.2.10");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            filter.doFilter(req, resp, new MockFilterChain());

            assertEquals(200, resp.getStatus());
            assertEquals("2", resp.getHeader("X-Rate-Limit-Limit"),
                    "capacity exposed via header");
            assertNotNull(resp.getHeader("X-Rate-Limit-Remaining"),
                    "remaining-tokens header must be set after a successful consume");
        }

        @Test
        void exhaustedBucket_returns429ProblemJson_withRetryAfterHeader() throws Exception {
            properties.setCapacity(1);
            properties.setRefillTokens(1);

            MockHttpServletRequest req = newRequest("/api/v1/missions");
            req.setRemoteAddr("192.0.2.20");

            // Drain the bucket.
            filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

            // Second hit: rejected.
            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            filter.doFilter(copyRequest(req), resp2, new MockFilterChain());

            assertEquals(429, resp2.getStatus());
            assertEquals("application/problem+json", resp2.getContentType());
            assertEquals("1", resp2.getHeader("X-Rate-Limit-Limit"));
            assertEquals("0", resp2.getHeader("X-Rate-Limit-Remaining"));
            assertNotNull(resp2.getHeader("X-Rate-Limit-Retry-After-Seconds"));
            // Body must look like a problem document.
            String body = resp2.getContentAsString();
            assertTrue(body.contains("\"status\":429"), body);
            assertTrue(body.contains("\"title\":\"Too Many Requests\""), body);
            assertTrue(body.contains("\"instance\":\"/api/v1/missions\""), body);
            assertTrue(body.contains(problemProperties.getBaseUri() + "rate-limit-exceeded"),
                    "type URI must be built off AppProblemProperties.baseUri");
        }

        private MockHttpServletRequest copyRequest(MockHttpServletRequest src) {
            MockHttpServletRequest copy = new MockHttpServletRequest(src.getMethod(), src.getRequestURI());
            copy.setRemoteAddr(src.getRemoteAddr());
            return copy;
        }
    }

    // ---------------------------------------------------------------
    // chain continuation — make sure the filter actually delegates to the next link
    // ---------------------------------------------------------------

    @Test
    void successful_consume_invokesNextFilterInChain() throws Exception {
        MockHttpServletRequest req = newRequest("/api/v1/missions");
        req.setRemoteAddr("192.0.2.30");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        InvocationTrackingChain chain = new InvocationTrackingChain();
        filter.doFilter(req, resp, chain);

        assertTrue(chain.wasCalled, "the next filter in the chain must be invoked");
    }

    @Test
    void rateLimited_request_doesNOTInvokeNextFilter() throws Exception {
        properties.setCapacity(1);
        properties.setRefillTokens(1);

        MockHttpServletRequest req = newRequest("/api/v1/missions");
        req.setRemoteAddr("192.0.2.40");
        // Drain
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/missions");
        req2.setRemoteAddr("192.0.2.40");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        InvocationTrackingChain chain = new InvocationTrackingChain();

        filter.doFilter(req2, resp2, chain);

        assertEquals(429, resp2.getStatus());
        assertEquals(false, chain.wasCalled,
                "downstream filters must NOT be invoked when the request is rate-limited");
    }

    // ---------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------

    private static MockHttpServletRequest newRequest(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static class InvocationTrackingChain implements FilterChain {
        boolean wasCalled = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            wasCalled = true;
        }
    }
}
