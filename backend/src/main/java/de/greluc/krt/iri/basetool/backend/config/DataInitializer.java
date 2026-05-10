package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final SquadronRepository squadronRepository;

    @Bean
    public CommandLineRunner initRoles() {
        return args -> {
            // Lookup is by `code`, not by `name`: an admin renaming a role no longer
            // triggers a silent re-create with default permissions on the next boot.
            createRoleIfNotFound("SQUADRON_MEMBER", "Squadron Member",
                    Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ"));
            createRoleIfNotFound("OFFICER", "Officer",
                    Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ", "MISSION_WRITE", "MISSION_MANAGE", "USER_MANAGE"));
            createRoleIfNotFound("ADMIN", "Admin",
                    Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ", "MISSION_WRITE", "MISSION_MANAGE", "USER_MANAGE", "ROLE_MANAGE"));
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
