package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the default roles and the IRIDIUM squadron on first startup.
 *
 * <p>Roles are matched by {@code code}, not by {@code name}, so an admin renaming a role in the
 * admin UI does not trigger a silent re-create with the default permissions on the next boot. The
 * permission sets here are the baseline a fresh DB needs to bring up the security model — admins
 * extend them at runtime via the role-management screens.
 */
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

  private final RoleRepository roleRepository;
  private final SquadronRepository squadronRepository;

  /**
   * Returns a {@link CommandLineRunner} that runs the role + squadron seeding exactly once at boot.
   * Each individual upsert is guarded by an existence check, so the runner is safe to invoke on a
   * non-empty database.
   *
   * @return the seeding runner Spring Boot executes after the context is ready
   */
  @Bean
  public CommandLineRunner initRoles() {
    return args -> {
      // Lookup is by `code`, not by `name`: an admin renaming a role no longer
      // triggers a silent re-create with default permissions on the next boot.
      createRoleIfNotFound(
          "SQUADRON_MEMBER",
          "Squadron Member",
          Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ"));
      createRoleIfNotFound(
          "OFFICER",
          "Officer",
          Set.of(
              "HANGAR_READ",
              "HANGAR_WRITE",
              "MISSION_READ",
              "MISSION_WRITE",
              "MISSION_MANAGE",
              "USER_MANAGE"));
      createRoleIfNotFound(
          "ADMIN",
          "Admin",
          Set.of(
              "HANGAR_READ",
              "HANGAR_WRITE",
              "MISSION_READ",
              "MISSION_WRITE",
              "MISSION_MANAGE",
              "USER_MANAGE",
              "ROLE_MANAGE"));
      createRoleIfNotFound("GUEST", "Guest", Set.of());

      createSquadronIfNotFound("IRIDIUM", "IRI", "The main squadron.");
    };
  }

  private void createRoleIfNotFound(String code, String displayName, Set<String> permissions) {
    if (roleRepository.findByCode(code).isPresent()) {
      return;
    }
    Role role = new Role();
    role.setCode(code);
    role.setName(displayName);
    role.setPermissions(new HashSet<>(permissions)); // Ensure mutable
    roleRepository.save(role);
  }

  private void createSquadronIfNotFound(String name, String shorthand, String description) {
    if (squadronRepository.findByShorthand(shorthand).isEmpty()) {
      Squadron sq = new Squadron();
      sq.setName(name);
      sq.setShorthand(shorthand);
      sq.setDescription(description);
      squadronRepository.save(sq);
    }
  }
}
