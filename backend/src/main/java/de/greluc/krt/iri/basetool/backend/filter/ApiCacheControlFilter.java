package de.greluc.krt.iri.basetool.backend.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds conservative Cache-Control headers for idempotent API GET responses to enable client-side
 * revalidation via ETag while avoiding stale caches for dynamic data.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiCacheControlFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      return true;
    }
    String uri = request.getRequestURI();
    return uri == null || !uri.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Set revalidation headers for dynamic GET endpoints
    response.setHeader("Cache-Control", "no-cache, must-revalidate");
    response.addHeader("Vary", "Accept-Encoding");
    filterChain.doFilter(request, response);
  }
}
