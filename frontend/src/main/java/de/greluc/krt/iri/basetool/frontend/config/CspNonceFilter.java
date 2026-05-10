package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates a per-request CSP nonce and exposes it via the request attribute
 * {@code cspNonce}. Thymeleaf templates reference it as {@code ${cspNonce}}
 * on every {@code <script>} tag; the same attribute is read by the CSP header
 * writer in {@link SecurityConfig} to emit
 * {@code script-src 'nonce-XYZ' 'strict-dynamic'}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CspNonceFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = "cspNonce";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 16;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        byte[] bytes = new byte[NONCE_BYTES];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.setAttribute(REQUEST_ATTRIBUTE, nonce);
        chain.doFilter(request, response);
    }
}
