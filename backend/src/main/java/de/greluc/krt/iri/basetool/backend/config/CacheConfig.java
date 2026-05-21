package de.greluc.krt.iri.basetool.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine-backed Spring cache manager for the project's reference-data caches.
 *
 * <p>Every cache has the same policy ({@code maximumSize=1000}, {@code expireAfterWrite=2m},
 * statistics recording on). 2&nbsp;minutes is short enough that admin edits in Keycloak / the
 * master-data screens become visible without manual cache eviction and long enough to amortize the
 * cost of the catalog lookups that fire on every list-page render. {@code setAllowNullValues=false}
 * forces services to never store {@code null} — a missed lookup must remain a miss so the next call
 * retries instead of caching the absence.
 */
@Configuration
@EnableCaching
public class CacheConfig {

  public static final String CITIES_CACHE = "cities";
  public static final String FREQUENCY_TYPES_CACHE = "frequencyTypes";
  public static final String JOB_TYPES_CACHE = "jobTypes";
  public static final String SQUADRONS_CACHE = "squadrons";
  public static final String LOCATIONS_CACHE = "locations";
  public static final String MANUFACTURERS_CACHE = "manufacturers";
  public static final String MATERIAL_CATEGORIES_CACHE = "materialCategories";
  public static final String MATERIALS_CACHE = "materials";
  public static final String MISSION_LEAD_TYPES_CACHE = "missionLeadTypes";
  public static final String REFINING_METHODS_CACHE = "refiningMethods";
  public static final String ROLES_CACHE = "roles";
  public static final String SHIP_TYPES_CACHE = "shipTypes";
  public static final String STAR_SYSTEMS_CACHE = "starSystems";

  /**
   * Builds the shared {@link CacheManager} with the project's standard Caffeine policy and the
   * fixed set of named caches above. Cache names are referenced from the {@code @Cacheable}
   * annotations on the service layer; an unknown name throws at startup rather than silently
   * creating a new cache.
   *
   * @return configured Caffeine cache manager
   */
  @Bean
  public CacheManager cacheManager() {
    Caffeine<Object, Object> builder =
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(2))
            .recordStats();

    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(builder);
    manager.setAllowNullValues(false);
    manager.setCacheNames(
        List.of(
            CITIES_CACHE,
            FREQUENCY_TYPES_CACHE,
            JOB_TYPES_CACHE,
            SQUADRONS_CACHE,
            LOCATIONS_CACHE,
            MANUFACTURERS_CACHE,
            MATERIAL_CATEGORIES_CACHE,
            MATERIALS_CACHE,
            MISSION_LEAD_TYPES_CACHE,
            REFINING_METHODS_CACHE,
            ROLES_CACHE,
            SHIP_TYPES_CACHE,
            STAR_SYSTEMS_CACHE));
    return manager;
  }
}
