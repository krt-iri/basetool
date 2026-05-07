package de.greluc.krt.iri.basetool.backend.logging;

import de.greluc.krt.iri.basetool.backend.config.LoggingProperties;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs a concise startup banner as soon as the application context is fully ready.
 *
 * <p>The banner surfaces the most important runtime facts a developer or on-call engineer needs
 * when triaging an incident:</p>
 * <ul>
 *   <li>active Spring profiles</li>
 *   <li>Keycloak issuer URI (public, no secret)</li>
 *   <li>Datasource URL with credentials <b>stripped</b></li>
 *   <li>effective logging configuration (correlation header, slow-request threshold, structured)</li>
 * </ul>
 *
 * <p>Secrets like {@code spring.datasource.password}, admin client secrets and tokens are never
 * logged. JDBC URLs are sanitised via {@link #sanitiseJdbcUrl(String)} to remove any
 * {@code user=}/{@code password=} query parameters that some drivers accept.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupBannerListener {

    private final Environment environment;
    private final LoggingProperties loggingProperties;

    @Value("${spring.datasource.url:unknown}")
    private String datasourceUrl;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:unknown}")
    private String keycloakIssuerUri;

    @Value("${spring.application.name:backend}")
    private String applicationName;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("============================================================");
        log.info(" IRIDIUM Basetool :: {} ready", applicationName);
        log.info(" Active profiles     : {}", Arrays.toString(environment.getActiveProfiles()));
        log.info(" Datasource URL      : {}", sanitiseJdbcUrl(datasourceUrl));
        log.info(" Keycloak issuer     : {}", keycloakIssuerUri);
        log.info(" Correlation header  : {}", loggingProperties.getCorrelationIdHeader());
        log.info(" Slow request (ms)   : {}", loggingProperties.getSlowRequestThresholdMs());
        log.info(" Structured logging  : {}", loggingProperties.isStructuredEnabled());
        log.info("============================================================");
    }

    /**
     * Removes user/password fragments from a JDBC URL. Handles both
     * {@code jdbc:postgresql://host:port/db?user=x&password=y} and the occasional
     * {@code jdbc:postgresql://user:pw@host/db}.
     */
    @NotNull
    static String sanitiseJdbcUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return "unknown";
        }
        String sanitised = url.replaceAll("(?i)([?&])(user|password)=[^&]*", "$1$2=***");
        sanitised = sanitised.replaceAll("(?i)(jdbc:[^/]+://)([^/@]+)@", "$1***@");
        return sanitised;
    }
}
