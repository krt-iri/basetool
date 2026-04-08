package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.RoleRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RoleDescriptionTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User adminUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("admin1");
        userRepository.save(adminUser);

        testRole = new Role();
        testRole.setName("TestRole");
        testRole = roleRepository.save(testRole);
    }

    @Test
    void testUpdateRoleDescription_Admin_Allowed() throws Exception {
        String newDescription = "This is a test description.";

        mockMvc.perform(put("/api/v1/admin/roles/" + testRole.getName() + "/description")
                .with(jwt().jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_MANAGE"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.TEXT_PLAIN)
                .content(newDescription)) 
                .andExpect(status().isOk());

        Role updatedRole = roleRepository.findByName(testRole.getName()).orElseThrow();
        assertEquals(newDescription, updatedRole.getDescription());
    }

    @Test
    void testUpdateRoleDescription_User_Forbidden() throws Exception {
        String newDescription = "Hacked description.";

        User regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setUsername("user1");
        userRepository.save(regularUser);

        mockMvc.perform(put("/api/v1/admin/roles/" + testRole.getName() + "/description")
                .with(jwt().jwt(builder -> builder.subject(regularUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))) // Not ADMIN
                .contentType(MediaType.TEXT_PLAIN)
                .content(newDescription))
                .andExpect(status().isForbidden());
    }
}
