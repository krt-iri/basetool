package de.greluc.krt.iri.basetool.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for the backend module.
 *
 * <p>{@code @SpringBootApplication} enables auto-configuration and component scanning rooted at
 * this package, {@code @EnableScheduling} activates the {@code @Scheduled} hooks used by the
 * Keycloak sync and other periodic tasks, and {@code @ConfigurationPropertiesScan} registers every
 * {@code @ConfigurationProperties} record under {@code config/} without requiring an explicit
 * {@code @EnableConfigurationProperties} list.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BackendApplication {

  /**
   * Standard Spring Boot main; delegates to {@link SpringApplication#run(Class, String...)} with
   * this class as the primary source.
   *
   * @param args command-line arguments forwarded to Spring Boot
   */
  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
