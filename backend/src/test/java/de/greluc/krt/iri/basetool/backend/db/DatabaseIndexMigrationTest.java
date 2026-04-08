package de.greluc.krt.iri.basetool.backend.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;

@Testcontainers
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class DatabaseIndexMigrationTest {

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private SquadronRepository squadronRepository;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("krt_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayMigrationAddsExpectedIndexes() {
        // Execute Flyway migrations (adds indexes/constraints)
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Verify user index
        List<String> userIdx = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'app_user' AND indexname = 'ux_app_user_username'",
                String.class);
        assertThat(userIdx).isNotEmpty();

        // Verify ship owner index
        List<String> shipOwnerIdx = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'ship' AND indexname = 'ix_ship_owner_id'",
                String.class);
        assertThat(shipOwnerIdx).isNotEmpty();

        // Verify refinery_order started_at index
        List<String> roStartedAtIdx = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'refinery_order' AND indexname = 'ix_refinery_order_started_at'",
                String.class);
        assertThat(roStartedAtIdx).isNotEmpty();

        // Verify mission planned start time index
        List<String> missionPlannedIdx = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'mission' AND indexname = 'ix_mission_planned_start_time'",
                String.class);
        assertThat(missionPlannedIdx).isNotEmpty();
    }
}
