package de.greluc.krt.iri.basetool.backend.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.greluc.krt.iri.basetool.backend.config.AppProblemProperties;
import de.greluc.krt.iri.basetool.backend.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitingFilter}. Previously had no test file at all — 36% branch
 * coverage. The most security-critical path is {@code resolveClientIp}, which decides which
 * "bucket" a request lands in:
 *
 * <ul>
 *   <li>If the immediate peer is on the trusted-proxies allow-list, the filter takes the
 *       original-client IP from {@code X-Forwarded-For}; otherwise the spoofable header is ignored.
 *   <li>The literal {@code "*"} is NOT a valid trust value (would let any client spoof the header
 *       and get a fresh bucket per request).
 * </ul>
 *
 * <p>The tests use Spring's {@link MockHttpServletRequest}/Response so no Spring context is needed.
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
    properties.setCapacity(2); // tight bucket so we can hit the limit quickly
    properties.setRefillTokens(2);
    properties.setRefillPeriod(Duration.ofMinutes(1));

    problemProperties = new AppProblemProperties();
    problemProperties.setBaseUri("https://profit-base.online/problems/");

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

      assertTrue(
          filter.shouldNotFilter(req), "enabled=false must short-circuit before path matching");
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

      assertEquals(
          false, filter.shouldNotFilter(req), "matching path must NOT be skipped (filter applies)");
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
      req.setRemoteAddr("198.51.100.10"); // NOT in trustedProxies
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
    void twoRequestsFromSameSpoofedXffShareABucket_whenTrustedProxyNotConfigured()
        throws Exception {
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

      assertEquals(
          429,
          resp2.getStatus(),
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
      assertEquals(
          429,
          resp2.getStatus(),
          "second request with the same resolved IP (" + expectedIp + ") must be rate-limited");
    }

    private MockHttpServletRequest copy(MockHttpServletRequest src) {
      MockHttpServletRequest copy =
          new MockHttpServletRequest(src.getMethod(), src.getRequestURI());
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
      assertEquals("2", resp.getHeader("X-Rate-Limit-Limit"), "capacity exposed via header");
      assertNotNull(
          resp.getHeader("X-Rate-Limit-Remaining"),
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
      assertTrue(
          body.contains(problemProperties.getBaseUri() + "rate-limit-exceeded"),
          "type URI must be built off AppProblemProperties.baseUri");
    }

    private MockHttpServletRequest copyRequest(MockHttpServletRequest src) {
      MockHttpServletRequest copy =
          new MockHttpServletRequest(src.getMethod(), src.getRequestURI());
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
    assertEquals(
        false,
        chain.wasCalled,
        "downstream filters must NOT be invoked when the request is rate-limited");
  }

  // ---------------------------------------------------------------
  // Endpoint-specific rules layered on top of the global default — audit finding L-5.
  // The per-rule budgets are designed to trip before the loose global budget on the
  // anonymous-spam POST endpoints (mission create, joborder create, finance-entry,
  // participant CRUD); the tests below pin the layered semantics.
  // ---------------------------------------------------------------

  @Nested
  class EndpointSpecificRuleTests {

    @Test
    void specificRule_runsOutFirst_andDoesNotDrainGlobalBucket() throws Exception {
      // Global default leaves plenty of headroom (10/min), the rule is tight (2/min). After 2
      // POSTs the per-rule bucket is empty -> 429, but the global bucket has only consumed 2 of
      // 10 tokens, so a different endpoint covered only by the global default still goes through.
      properties.setCapacity(10);
      properties.setRefillTokens(10);

      RateLimitProperties.Rule missionCreate =
          newRule("mission-create", List.of("POST"), List.of("/api/v1/missions"), 2);
      properties.setRules(List.of(missionCreate));

      // Drain the per-rule bucket with two POSTs to /api/v1/missions.
      assertEquals(200, post("/api/v1/missions", "192.0.2.50"));
      assertEquals(200, post("/api/v1/missions", "192.0.2.50"));
      // Third POST: rule depleted -> 429.
      MockHttpServletResponse blocked = postResponse("/api/v1/missions", "192.0.2.50");
      assertEquals(429, blocked.getStatus());
      // Headers attribute the rejection to the per-rule limit (2), not the global (10).
      assertEquals("2", blocked.getHeader("X-Rate-Limit-Limit"));

      // Different endpoint, same IP -> global bucket still has tokens -> 200. Proves the per-rule
      // failure short-circuited without draining the global bucket for unrelated paths.
      assertEquals(200, get("/api/v1/orders", "192.0.2.50"));
    }

    @Test
    void specificRule_consumed_alsoDebitsGlobalBucket() throws Exception {
      // The global bucket DOES still tick on every request that matches a tight rule — that's the
      // layered "defense-in-depth" semantics: an attacker who finds an unlimited rule can't use
      // it to escape the global cap.
      properties.setCapacity(3);
      properties.setRefillTokens(3);

      RateLimitProperties.Rule missionCreate =
          newRule(
              "mission-create",
              List.of("POST"),
              List.of("/api/v1/missions"),
              100); // huge per-rule budget
      properties.setRules(List.of(missionCreate));

      // Three POSTs drain the global bucket.
      assertEquals(200, post("/api/v1/missions", "192.0.2.60"));
      assertEquals(200, post("/api/v1/missions", "192.0.2.60"));
      assertEquals(200, post("/api/v1/missions", "192.0.2.60"));
      // Fourth POST: global bucket empty -> 429 with the GLOBAL capacity reflected in the header
      // (the per-rule bucket is the loose one this time).
      MockHttpServletResponse blocked = postResponse("/api/v1/missions", "192.0.2.60");
      assertEquals(429, blocked.getStatus());
      assertEquals("3", blocked.getHeader("X-Rate-Limit-Limit"));
    }

    @Test
    void specificRule_doesNotApply_whenMethodMismatches() throws Exception {
      // The rule targets POST; a GET to the same path must NOT be limited by the rule. The global
      // bucket (capacity 10) is the only check.
      properties.setCapacity(10);
      properties.setRefillTokens(10);

      RateLimitProperties.Rule missionCreate =
          newRule("mission-create", List.of("POST"), List.of("/api/v1/missions"), 1);
      properties.setRules(List.of(missionCreate));

      // Three GETs to /api/v1/missions are fine even though the per-rule capacity is 1.
      assertEquals(200, get("/api/v1/missions", "192.0.2.70"));
      assertEquals(200, get("/api/v1/missions", "192.0.2.70"));
      assertEquals(200, get("/api/v1/missions", "192.0.2.70"));
    }

    @Test
    void specificRule_emptyMethodsList_matchesAnyMethod() throws Exception {
      // Empty methods list means "any HTTP method" — mirrors how Spring's @RequestMapping treats
      // an empty methods array.
      properties.setCapacity(10);
      properties.setRefillTokens(10);

      RateLimitProperties.Rule mutateParticipants =
          newRule(
              "participant-mutations", List.of(), List.of("/api/v1/missions/*/participants/**"), 1);
      properties.setRules(List.of(mutateParticipants));

      assertEquals(200, put("/api/v1/missions/abc/participants/xyz/slim", "192.0.2.71"));
      // Second mutation of any kind on a participants sub-resource -> 429.
      MockHttpServletResponse blocked =
          postResponse("/api/v1/missions/abc/participants/xyz/check-in/slim", "192.0.2.71");
      assertEquals(429, blocked.getStatus());
    }

    @Test
    void twoRulesMatchSameRequest_tightestRunsOutFirst() throws Exception {
      // Two overlapping rules: a wider participants umbrella (5/min) and a tighter check-in slot
      // (2/min). The filter must trip the tightest first; the wider one only kicks in if the
      // request streams keep hitting after the tight cap recovers.
      properties.setCapacity(50);
      properties.setRefillTokens(50);

      RateLimitProperties.Rule wide =
          newRule("participant-wide", List.of(), List.of("/api/v1/missions/*/participants/**"), 5);
      RateLimitProperties.Rule tight =
          newRule(
              "participant-check-in",
              List.of("POST"),
              List.of("/api/v1/missions/*/participants/*/check-in/slim"),
              2);
      properties.setRules(List.of(wide, tight));

      String path = "/api/v1/missions/m1/participants/p1/check-in/slim";
      assertEquals(200, post(path, "192.0.2.80"));
      assertEquals(200, post(path, "192.0.2.80"));
      // Third hit: tight bucket exhausted; wide still has 3 tokens, global has 47.
      MockHttpServletResponse blocked = postResponse(path, "192.0.2.80");
      assertEquals(429, blocked.getStatus());
      // The 429 must attribute the rejection to the tight rule (capacity 2).
      assertEquals("2", blocked.getHeader("X-Rate-Limit-Limit"));
    }

    @Test
    void specificRule_buckets_perIp_independently() throws Exception {
      // The per-rule bucket key includes the client IP, so a flooder on one IP cannot starve a
      // legitimate user on another IP.
      properties.setCapacity(100);
      properties.setRefillTokens(100);

      RateLimitProperties.Rule missionCreate =
          newRule("mission-create", List.of("POST"), List.of("/api/v1/missions"), 1);
      properties.setRules(List.of(missionCreate));

      // IP A drains its rule bucket.
      assertEquals(200, post("/api/v1/missions", "203.0.113.1"));
      assertEquals(429, postResponse("/api/v1/missions", "203.0.113.1").getStatus());
      // IP B still has its own fresh bucket.
      assertEquals(200, post("/api/v1/missions", "203.0.113.2"));
    }

    private RateLimitProperties.Rule newRule(
        String name, List<String> methods, List<String> paths, int capacity) {
      RateLimitProperties.Rule r = new RateLimitProperties.Rule();
      r.setName(name);
      r.setMethods(methods);
      r.setPaths(paths);
      r.setCapacity(capacity);
      r.setRefillTokens(capacity);
      r.setRefillPeriod(Duration.ofMinutes(1));
      return r;
    }

    private int post(String path, String ip) throws ServletException, IOException {
      return postResponse(path, ip).getStatus();
    }

    private MockHttpServletResponse postResponse(String path, String ip)
        throws ServletException, IOException {
      MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
      req.setRemoteAddr(ip);
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilter(req, resp, new MockFilterChain());
      return resp;
    }

    private int get(String path, String ip) throws ServletException, IOException {
      MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
      req.setRemoteAddr(ip);
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilter(req, resp, new MockFilterChain());
      return resp.getStatus();
    }

    private int put(String path, String ip) throws ServletException, IOException {
      MockHttpServletRequest req = new MockHttpServletRequest("PUT", path);
      req.setRemoteAddr(ip);
      MockHttpServletResponse resp = new MockHttpServletResponse();
      filter.doFilter(req, resp, new MockFilterChain());
      return resp.getStatus();
    }
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
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      wasCalled = true;
    }
  }
}
