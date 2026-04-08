package de.greluc.krt.iri.basetool.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    /** Enable/disable the rate limiter globally */
    private boolean enabled = true;
    /** Ant-style path patterns to protect (e.g. /api/v1/admin/**) */
    @NotEmpty
    private List<String> paths = java.util.List.of("/api/v1/admin/**");
    /** Bucket capacity (max tokens) */
    @Min(1)
    private int capacity = 100;
    /** Tokens refilled per period */
    @Min(1)
    private int refillTokens = 100;
    /** Refill period */
    @NotNull
    private Duration refillPeriod = Duration.ofMinutes(1);
    /** List of trusted proxy IPs. If empty, the X-Forwarded-For header is ignored. Set to ["*"] to trust all (USE WITH CAUTION). */
    private List<String> trustedProxies = new java.util.ArrayList<>();
}
