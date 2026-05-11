package de.greluc.krt.iri.basetool.frontend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Servlet filter that intercepts known bot, scanner, and exploit requests before they reach
 * the Spring Security filter chain.
 *
 * <p>Three independent detection strategies are applied in order:
 * <ol>
 *   <li><b>Path-prefix matching:</b> URIs starting with known bot/scanner path prefixes
 *       (e.g. {@code /wp-admin/}, {@code /actuator}, {@code /.env}) are answered with
 *       HTTP 404 immediately.</li>
 *   <li><b>File-extension matching:</b> Requests for file types the application never serves
 *       (e.g. {@code .php}, {@code .asp}, {@code .sql}) are answered with HTTP 404.</li>
 *   <li><b>HTTP-method filtering:</b> Requests using methods the application never uses
 *       (e.g. {@code TRACE}, {@code CONNECT}, {@code PROPFIND}) are answered with HTTP 405.</li>
 * </ol>
 *
 * <p>This filter runs before Spring Security, so bot requests never trigger an OAuth2 flow
 * or load the security context — reducing unnecessary load on Keycloak and the application.
 *
 * <p>All detection is case-insensitive to prevent trivial bypass attempts.
 */
@Component
@Slf4j
public class BotProtectionFilter extends OncePerRequestFilter {

    /**
     * URI path prefixes that are known to originate from automated scanners, bots, or exploit
     * attempts. Requests whose URI starts with any of these prefixes (case-insensitive) are
     * answered with HTTP 404 without further processing.
     */
    static final Set<String> BOT_PATH_PREFIXES = Set.of(
            // WordPress / CMS
            "/wp-", "/wordpress", "/xmlrpc",
            // PHP / generic web-app scanners
            "/phpmyadmin",
            // Feed / author / sitemap (WordPress-style)
            "/feed", "/author", "/sitemap",
            // Dot-files and hidden config
            "/.env", "/.git", "/.svn", "/.htaccess", "/.htpasswd", "/.ds_store",
            // Generic config / backup / shell paths
            "/config", "/backup", "/shell", "/cgi-bin", "/vendor",
            // Spring Boot / Java management endpoints
            "/actuator", "/console", "/manager", "/jolokia", "/jmx",
            // Well-known ACME challenge (scanner abuse)
            "/.well-known/acme-challenge",
            // Laravel / PHP framework paths
            "/telescope", "/horizon", "/nova", "/laravel",
            // Router / IoT exploits
            "/boaform", "/gponform", "/setup.cgi",
            // Microsoft Exchange / Outlook Web Access
            "/owa", "/autodiscover", "/ecp", "/ews",
            // Other known exploit targets
            "/solr", "/jenkins", "/hudson", "/jira", "/confluence",
            // API discovery scans (not present in frontend)
            "/swagger-ui", "/api-docs",
            // Debug / trace endpoints
            "/debug", "/trace",
            // robots.txt (not served by this app)
            "/robots.txt"
    );

    /**
     * File extensions that the application never serves. Requests for files with these
     * extensions (case-insensitive) are answered with HTTP 404.
     */
    static final Set<String> BOT_FILE_EXTENSIONS = Set.of(
            ".php", ".asp", ".aspx", ".cgi", ".pl", ".py", ".rb",
            ".cfm", ".sql", ".bak", ".old", ".swp", ".env", ".ini",
            ".log", ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
            ".xml.gz", ".tar", ".tar.gz", ".zip", ".rar", ".7z"
    );

    /**
     * HTTP methods that the application actively uses. Any request using a method not in
     * this set is answered with HTTP 405 (Method Not Allowed).
     */
    static final Set<String> ALLOWED_HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 1. HTTP method check
        if (!ALLOWED_HTTP_METHODS.contains(method.toUpperCase())) {
            log.debug("Blocked disallowed HTTP method: {} {}", method, uri);
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        // 2. Path-prefix check
        if (isBotPath(uri)) {
            log.debug("Blocked bot/scanner path: {} {}", method, uri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 3. File-extension check
        if (isBotFileExtension(uri)) {
            log.debug("Blocked bot/scanner file extension: {} {}", method, uri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * URIs that look like bot/scanner targets at first glance but are in fact legitimate
     * endpoints the application owns. They short-circuit {@link #isBotPath(String)} so the
     * regular filter chain (and Spring Security) gets a chance to handle them.
     *
     * <p>Currently only the Spring Boot Actuator health endpoint is whitelisted — it is
     * exposed publicly so the Docker {@code HEALTHCHECK} directive can reach it without
     * authentication; every other {@code /actuator/...} path stays blocked.
     */
    static final Set<String> LEGITIMATE_PATHS = Set.of(
            "/actuator/health"
    );

    /**
     * Returns {@code true} if the given URI starts with a known bot/scanner path prefix.
     *
     * @param uri the request URI to check (must not be {@code null})
     * @return {@code true} if the URI matches a bot path prefix
     */
    boolean isBotPath(@NotNull String uri) {
        String lowerUri = uri.toLowerCase();
        for (String legit : LEGITIMATE_PATHS) {
            if (lowerUri.equals(legit) || lowerUri.startsWith(legit + "/")) {
                return false;
            }
        }
        for (String prefix : BOT_PATH_PREFIXES) {
            if (lowerUri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given URI ends with a file extension that the application
     * never serves.
     *
     * @param uri the request URI to check (must not be {@code null})
     * @return {@code true} if the URI ends with a known bot file extension
     */
    boolean isBotFileExtension(@NotNull String uri) {
        String lowerUri = uri.toLowerCase();
        for (String ext : BOT_FILE_EXTENSIONS) {
            if (lowerUri.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
