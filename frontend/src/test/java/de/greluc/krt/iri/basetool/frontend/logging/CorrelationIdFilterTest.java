package de.greluc.krt.iri.basetool.frontend.logging;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.frontend.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

class CorrelationIdFilterTest {

    private final LoggingProperties props = new LoggingProperties();
    private final CorrelationIdFilter filter = new CorrelationIdFilter(props);

    @AfterEach
    void tearDown() {
        MDC.clear();
        CorrelationContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        String cid = res.getHeader("X-Correlation-Id");
        assertThat(cid).isNotBlank();
        // UUID length including hyphens
        assertThat(cid).matches("[0-9a-fA-F-]{36}");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("userId")).isNull();
        assertThat(CorrelationContext.get()).isNull();
    }

    @Test
    void reusesInboundCorrelationId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("X-Correlation-Id", "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getHeader("X-Correlation-Id")).isEqualTo("abc-123");
    }

    @Test
    void rejectsUnsafeInboundCorrelationId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("X-Correlation-Id", "evil\r\nInjected: header");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        String cid = res.getHeader("X-Correlation-Id");
        assertThat(cid).doesNotContain("\r").doesNotContain("\n");
        assertThat(cid).matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void exposesCorrelationIdToCorrelationContextDuringChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("X-Correlation-Id", "thread-local-check");
        MockHttpServletResponse res = new MockHttpServletResponse();
        final String[] seen = new String[1];
        FilterChain chain = (request, response) -> seen[0] = CorrelationContext.get();

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isEqualTo("thread-local-check");
        assertThat(CorrelationContext.get()).isNull();
    }

    @Test
    void setsAnonymousUserIdWhenUnauthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse res = new MockHttpServletResponse();
        final String[] seen = new String[1];
        FilterChain chain = (request, response) -> seen[0] = MDC.get("userId");

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isEqualTo("anonymous");
    }
}
