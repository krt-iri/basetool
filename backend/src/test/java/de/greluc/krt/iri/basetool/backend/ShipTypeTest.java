package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.ManufacturerRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShipTypeTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private ShipTypeRepository shipTypeRepository;

  @Autowired private ManufacturerRepository manufacturerRepository;

  @Autowired private ShipRepository shipRepository;

  @Autowired private UserRepository userRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private User guestUser;
  private Manufacturer aegis;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officerShip");
    userRepository.save(officerUser);

    guestUser = new User();
    guestUser.setId(UUID.randomUUID());
    guestUser.setUsername("guestShip");
    userRepository.save(guestUser);

    aegis = new Manufacturer();
    aegis.setName("Aegis Dynamics");
    aegis.setAbbreviation("AEGIS");
    manufacturerRepository.save(aegis);
  }

  @Test
  void testToggleShipTypeVisibility_Admin_Allowed() throws Exception {
    ShipType shipType = new ShipType();
    shipType.setName("Light Fighter");
    shipType.setManufacturer(aegis);
    shipType.setDescription("Fast and agile");
    shipType.setHidden(false);
    shipType = shipTypeRepository.save(shipType);

    mockMvc
        .perform(
            put("/api/v1/ship-types/" + shipType.getId() + "/visibility?hidden=true")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());

    ShipType saved = shipTypeRepository.findAll().get(0);
    assertTrue(saved.isHidden());
  }

  @Test
  void testToggleShipTypeVisibility_Guest_Forbidden() throws Exception {
    ShipType shipType = new ShipType();
    shipType.setName("Hacked Ship");
    shipType.setHidden(false);
    shipType = shipTypeRepository.save(shipType);

    mockMvc
        .perform(
            put("/api/v1/ship-types/" + shipType.getId() + "/visibility?hidden=true")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
        .andExpect(status().isForbidden());
  }
}
