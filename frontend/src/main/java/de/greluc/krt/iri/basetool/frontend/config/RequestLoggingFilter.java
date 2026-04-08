package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String remoteAddr = request.getRemoteAddr();
        
        // Skip logging for common static resources to keep logs clean
        boolean isStatic = uri.endsWith(".css") || uri.endsWith(".js") || uri.contains("/images/") || uri.contains("/logos/") || uri.contains("/fonts/");
        
        if (!isStatic) {
            String csrfHeader = request.getHeader("X-CSRF-TOKEN");
            log.info("[DEBUG_LOG] RequestLoggingFilter START: {} {} from {} (CSRF header: {})", 
                method, uri, remoteAddr, (csrfHeader != null));
        }
        
        try {
            filterChain.doFilter(request, response);
            if (!isStatic) {
                log.info("[DEBUG_LOG] RequestLoggingFilter END: {} {} -> Status {}", 
                    method, uri, response.getStatus());
            }
        } catch (Exception e) {
            if (!isStatic) {
                log.error("[DEBUG_LOG] RequestLoggingFilter ERROR: {} {} -> Exception: {}", 
                    method, uri, e.getMessage());
            }
            throw e;
        }
    }
}
