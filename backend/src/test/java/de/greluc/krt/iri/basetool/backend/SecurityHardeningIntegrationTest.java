package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
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
public class SecurityHardeningIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private User regularUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        regularUser = new User();
        regularUser.setId(UUID.randomUUID());
        regularUser.setUsername("regular_user");
        userRepository.save(regularUser);

        adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("admin_user");
        userRepository.save(adminUser);
    }

    @Test
    void testUserSearch_RegularUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/users/search").param("query", "test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUserSearch_Admin_Ok() throws Exception {
        mockMvc.perform(get("/api/v1/users/search").param("query", "test")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void testInventoryAggregated_RegularUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/aggregated")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void testInventoryMyInventory_RegularUser_Ok() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/my-inventory")
                .with(jwt().jwt(j -> j.subject(regularUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
                .andExpect(status().isOk());
    }

    @Test
    void testMissionCreate_RegularUser_Ok() throws Exception {
        mockMvc.perform(post("/api/v1/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Member Mission\"}")
                .with(jwt().jwt(b -> b.subject(java.util.UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
                .andExpect(status().isOk());
    }

    @Test
    void testMissionGet_Anonymous_Ok() throws Exception {
        mockMvc.perform(get("/api/v1/missions"))
                .andExpect(status().isOk());
    }

    @Test
    void testFinanceEntries_Anonymous_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/missions/" + UUID.randomUUID() + "/finance-entries"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testFinanceEntriesSum_Anonymous_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/missions/" + UUID.randomUUID() + "/finance-entries/sum"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRefineryOrders_Anonymous_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/refinery-orders/mission/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
