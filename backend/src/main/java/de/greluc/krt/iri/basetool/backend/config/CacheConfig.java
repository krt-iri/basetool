package de.greluc.krt.iri.basetool.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  public static final String JOB_TYPES_CACHE = "jobTypes";
  public static final String SQUADRONS_CACHE = "squadrons";
  public static final String LOCATIONS_CACHE = "locations";
  public static final String MANUFACTURERS_CACHE = "manufacturers";
  public static final String MATERIALS_CACHE = "materials";
  public static final String MISSION_LEAD_TYPES_CACHE = "missionLeadTypes";
  public static final String REFINING_METHODS_CACHE = "refiningMethods";
  public static final String ROLES_CACHE = "roles";
  public static final String SHIP_TYPES_CACHE = "shipTypes";
  public static final String STAR_SYSTEMS_CACHE = "starSystems";

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
            JOB_TYPES_CACHE,
            SQUADRONS_CACHE,
            LOCATIONS_CACHE,
            MANUFACTURERS_CACHE,
            MATERIALS_CACHE,
            MISSION_LEAD_TYPES_CACHE,
            REFINING_METHODS_CACHE,
            ROLES_CACHE,
            SHIP_TYPES_CACHE,
            STAR_SYSTEMS_CACHE));
    return manager;
  }
}
