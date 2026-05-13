package de.greluc.krt.iri.basetool.frontend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration for Cache. */
@Configuration
@EnableCaching
public class CacheConfig {

  public static final String STATIC_DATA_CACHE = "staticData";

  /**
   * Caffeine-backed {@link CacheManager} exposing the {@link #STATIC_DATA_CACHE} cache (10-minute
   * TTL, max 1000 entries). Used by {@code BackendApiClient.getCached(...)} for slow-changing
   * lookup data.
   */
  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager(STATIC_DATA_CACHE);
    cacheManager.setCaffeine(
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).maximumSize(1000));
    return cacheManager;
  }
}
