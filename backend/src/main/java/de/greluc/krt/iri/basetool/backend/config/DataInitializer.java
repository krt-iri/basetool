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
            createRoleIfNotFound("Squadron Member", Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ"));
            createRoleIfNotFound("Officer", Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ", "MISSION_WRITE", "MISSION_MANAGE", "USER_MANAGE"));
            createRoleIfNotFound("Admin", Set.of("HANGAR_READ", "HANGAR_WRITE", "MISSION_READ", "MISSION_WRITE", "MISSION_MANAGE", "USER_MANAGE", "ROLE_MANAGE"));
            createRoleIfNotFound("Guest", Set.of());

            createSquadronIfNotFound("IRIDIUM", "IRI", "The main squadron.");
        };
    }

    private void createRoleIfNotFound(String name, Set<String> permissions) {
        if (roleRepository.findByName(name).isEmpty()) {
            Role role = new Role();
            role.setName(name);
            role.setPermissions(new HashSet<>(permissions)); // Ensure mutable
            roleRepository.save(role);
        }
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
