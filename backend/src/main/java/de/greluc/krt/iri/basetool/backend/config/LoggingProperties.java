package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration for structured logging, MDC correlation and slow-request detection.
 *
 * <p>Bound under the {@code app.logging.*} prefix in {@code application*.yml}. Because all fields
 * are validated via Jakarta-Validation, any misconfiguration fails the application context start
 * early (see {@code LoggingPropertiesBindingTest} for the contract).
 */
@Getter
@Setter
@ToString
@Validated
@ConfigurationProperties(prefix = "app.logging")
public class LoggingProperties {

  /**
   * HTTP header used to accept an inbound correlation id and echo the effective one back. The value
   * is exposed via {@code X-Correlation-Id} by default, matching the widely used de-facto standard
   * across gateways/proxies.
   */
  @NotBlank private String correlationIdHeader = "X-Correlation-Id";

  /**
   * MDC key under which the correlation id is stored for the duration of a request. Must stay in
   * sync with the {@code %X{correlationId}} placeholder in {@code logback-spring.xml}.
   */
  @NotBlank private String correlationIdMdcKey = "correlationId";

  /**
   * MDC key under which the authenticated user's JWT {@code sub} claim is stored. Intentionally
   * limited to {@code sub} to avoid leaking names, emails or token contents into log files.
   */
  @NotBlank private String userIdMdcKey = "userId";

  /**
   * MDC key under which the resolved squadron context of the current request is stored - either the
   * caller's persistent {@code app_user.squadron_id}, the admin's active switcher selection, or the
   * sentinel {@code none}/{@code all} when no squadron applies. Mirrors the MULTI_SQUADRON_PLAN.md
   * section 7 logging requirement; keep this in sync with the {@code %X{squadronId}} placeholder in
   * {@code logback-spring.xml}.
   *
   * <p>R5.e / SPEZIALKOMMANDO_PLAN.md R14: this MDC key is being renamed to {@code orgUnitId} (see
   * {@link #orgUnitIdMdcKey}). For one release {@link
   * de.greluc.krt.iri.basetool.backend.logging.CorrelationIdFilter} populates BOTH keys with the
   * same value so log-pipeline dashboards can migrate at their own pace. The legacy {@code
   * squadronId} key comes out together with the legacy {@code X-Active-Squadron-Id} header alias
   * once the new field has soaked one release cycle.
   */
  @NotBlank private String squadronIdMdcKey = "squadronId";

  /**
   * R5.e / SPEZIALKOMMANDO_PLAN.md R14 replacement for {@link #squadronIdMdcKey}. The post-rename
   * MDC key under which {@link de.greluc.krt.iri.basetool.backend.logging.CorrelationIdFilter}
   * stores the resolved OrgUnit context of the current request. Plan §3.5 widens the concept from
   * "the user's Staffel" to "the user's active OrgUnit (Staffel or Spezialkommando, possibly the
   * union of memberships)" — same set of sentinel values ({@code anonymous} / {@code none} / {@code
   * all} / a UUID), the name just stops implying Staffel-only. Both this key and {@link
   * #squadronIdMdcKey} are written for one release; downstream dashboards should switch to this
   * name and drop the legacy alias on next deploy.
   */
  @NotBlank private String orgUnitIdMdcKey = "orgUnitId";

  /**
   * Requests taking longer than this threshold (in milliseconds) are logged at {@code WARN} instead
   * of {@code INFO} by {@code RequestLoggingFilter}. Set to a large value to disable.
   */
  @Min(0)
  private long slowRequestThresholdMs = 2000L;

  /**
   * Feature flag for structured (JSON) logging. The {@code logback-spring.xml} activates the JSON
   * appender only when this property is {@code true} (typically in production).
   */
  private boolean structuredEnabled = false;
}
