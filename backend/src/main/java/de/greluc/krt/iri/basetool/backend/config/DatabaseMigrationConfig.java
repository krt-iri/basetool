package de.greluc.krt.iri.basetool.backend.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseMigrationConfig {

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
