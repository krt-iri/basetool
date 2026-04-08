package de.greluc.krt.iri.basetool.backend.security;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.config.CustomJwtGrantedAuthoritiesConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LogisticianRoleTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomJwtGrantedAuthoritiesConverter converter;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void converterShouldAddLogisticianRole() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("logistician_user");
        user.setLogistician(true);
        userRepository.save(user);
        userRepository.flush();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", userId.toString())
                .claim("preferred_username", "logistician_user")
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        assertTrue(authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN")),
                "Should have ROLE_LOGISTICIAN");
    }

    @Test
    void adminShouldAccessInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void officerShouldAccessInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
                .andExpect(status().isOk());
    }

    @Test
    void memberWithLogisticianRoleShouldAccessInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"))))
                .andExpect(status().isOk());
    }

    @Test
    void realRequestShouldHaveLogisticianRole() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("test_logistician");
        user.setLogistician(true);
        userRepository.save(user);
        userRepository.flush();

        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().jwt(j -> j.subject(userId.toString()).claim("preferred_username", "test_logistician"))
                        .authorities(converter)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    void memberWithoutFlagShouldAccessInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk());
    }

    @Test
    void memberWithoutFlagShouldAccessMaterialInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/material/" + UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isNotFound()); // NotFound because material ID doesn't exist, but NOT 403
    }

    @Test
    void memberWithoutFlagShouldAccessAllInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/all")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk());
    }
}
