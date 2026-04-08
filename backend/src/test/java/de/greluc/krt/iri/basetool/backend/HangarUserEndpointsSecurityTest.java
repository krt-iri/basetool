package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HangarUserEndpointsSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipTypeRepository shipTypeRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User user;
    private ShipType shipType;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("hanger_user");
        userRepository.save(user);

        shipType = new ShipType();
        shipType.setName("Aurora");
        shipTypeRepository.save(shipType);
    }

    @Test
    void testMyShips_ReadAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/hangar/my-ships")
                        .with(jwt().jwt(j -> j.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("HANGAR_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void testMyShips_NoReadAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/hangar/my-ships")
                        .with(jwt().jwt(j -> j.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void testAddShip_WriteAllowed() throws Exception {
        String body = "{" +
                "\"name\": \"MyShip\"," +
                "\"shipTypeId\": \"" + shipType.getId() + "\"," +
                "\"insurance\": \"0\"," +
                "\"fitted\": false" +
                "}";
        mockMvc.perform(post("/api/v1/hangar/ships")
                        .with(jwt().jwt(j -> j.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("HANGAR_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void testAddShip_NoWriteAllowed() throws Exception {
        String body = "{" +
                "\"name\": \"MyShip\"," +
                "\"shipTypeId\": \"" + shipType.getId() + "\"," +
                "\"insurance\": \"0\"," +
                "\"fitted\": false" +
                "}";
        mockMvc.perform(post("/api/v1/hangar/ships")
                        .with(jwt().jwt(j -> j.subject(user.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("HANGAR_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
