package de.greluc.krt.iri.basetool.backend.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Forces Flyway to run before Hibernate validates the schema.
 *
 * <p>Spring Boot's default auto-configuration order is good enough for most apps, but this project
 * sets {@code spring.jpa.hibernate.ddl-auto=validate} everywhere (see CLAUDE.md) and {@code
 * EntityManagerFactory} bean creation runs validation immediately. Without an explicit ordering, an
 * EMF created before Flyway finishes migrating sees a stale or empty schema and aborts the
 * application. A {@link BeanPostProcessor} hooked to the {@code entityManagerFactory} bean name
 * runs the migration in its {@code postProcessBeforeInitialization} phase, guaranteeing the order.
 * Honoring {@code spring.flyway.enabled=false} keeps the {@code test} profile able to skip
 * migrations when the test harness sets up the schema differently.
 */
@Configuration
public class DatabaseMigrationConfig {

  /**
   * Registers a {@link BeanPostProcessor} that runs Flyway's {@code migrate()} just before the
   * {@code entityManagerFactory} bean is initialized so Hibernate's {@code validate} runs against
   * an up-to-date schema.
   *
   * @param dataSourceProvider lazy provider so we never inject the {@code DataSource} until the
   *     processor actually fires
   * @param env environment used to short-circuit when {@code spring.flyway.enabled=false}
   * @return the registered post-processor
   */
  @Bean
  public static BeanPostProcessor flywayMigrationBeanPostProcessor(
      ObjectProvider<DataSource> dataSourceProvider, Environment env) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String beanName)
          throws BeansException {
        if ("entityManagerFactory".equals(beanName)) {
          String enabled = env.getProperty("spring.flyway.enabled", "true");
          if ("true".equalsIgnoreCase(enabled)) {
            Flyway flyway =
                Flyway.configure()
                    .dataSource(dataSourceProvider.getObject())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
          }
        }
        return bean;
      }
    };
  }
}
