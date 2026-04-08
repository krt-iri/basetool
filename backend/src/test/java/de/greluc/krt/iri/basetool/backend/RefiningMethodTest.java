package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefiningMethodTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private RefiningMethodRepository refiningMethodRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User officerUser;
    private User guestUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setUsername("officerMethod");
        userRepository.save(officerUser);

        guestUser = new User();
        guestUser.setId(UUID.randomUUID());
        guestUser.setUsername("guestMethod");
        userRepository.save(guestUser);
    }

    @Test
    void testCreateRefiningMethod_Officer_Allowed() throws Exception {
        RefiningMethod method = new RefiningMethod();
        method.setName("New Method");
        method.setDescription("Best method");

        mockMvc.perform(post("/api/v1/refining-methods")
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(method)))
                .andExpect(status().isOk());
        
        assertEquals(1, refiningMethodRepository.findAll().size());
        assertEquals("New Method", refiningMethodRepository.findAll().get(0).getName());
    }

    @Test
    void testCreateRefiningMethod_Guest_Forbidden() throws Exception {
        RefiningMethod method = new RefiningMethod();
        method.setName("Hacked Method");

        mockMvc.perform(post("/api/v1/refining-methods")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(method)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateRefiningMethod_Officer_Allowed() throws Exception {
        RefiningMethod method = new RefiningMethod();
        method.setName("Old Name");
        method = refiningMethodRepository.save(method);

        method.setName("New Name");

        mockMvc.perform(put("/api/v1/refining-methods/" + method.getId())
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(method)))
                .andExpect(status().isOk());
        
        RefiningMethod loaded = refiningMethodRepository.findById(method.getId()).orElseThrow();
        assertEquals("New Name", loaded.getName());
    }

    @Test
    void testDeleteRefiningMethod_Officer_Allowed() throws Exception {
        RefiningMethod method = new RefiningMethod();
        method.setName("To Delete");
        method = refiningMethodRepository.save(method);

        mockMvc.perform(delete("/api/v1/refining-methods/" + method.getId())
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE"))))
                .andExpect(status().isOk());
        
        assertTrue(refiningMethodRepository.findById(method.getId()).isEmpty());
    }
}
