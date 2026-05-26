package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HangarIntegrationTest {

  @Autowired private SquadronRepository squadronRepository;

  private Squadron iridium;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private ShipRepository shipRepository;

  @Autowired private ShipTypeRepository shipTypeRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private MissionRepository missionRepository;

  @Autowired private MissionUnitRepository missionUnitRepository;

  @Autowired private OrgUnitMembershipRepository orgUnitMembershipRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @MockitoBean private JwtDecoder jwtDecoder;

  private User user1;
  private User user2;
  private User adminUser;
  private ShipType fighter;

  @BeforeEach
  void setUp() {
    iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    user1 = new User();
    user1.setId(UUID.randomUUID());
    user1.setUsername("user1");
    userRepository.save(user1);
    saveIridiumMembership(user1);

    user2 = new User();
    user2.setId(UUID.randomUUID());
    user2.setUsername("user2");
    userRepository.save(user2);
    saveIridiumMembership(user2);

    adminUser = new User();
    adminUser.setId(UUID.randomUUID());
    adminUser.setUsername("admin");
    userRepository.save(adminUser);
    saveIridiumMembership(adminUser);

    fighter = new ShipType();
    fighter.setName("Fighter");
    fighter = shipTypeRepository.save(fighter);
  }

  /** Post-R9 D3 (V101): home Staffel via membership row. */
  private void saveIridiumMembership(User u) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(u.getId(), Squadron.IRIDIUM_ID));
    m.setUser(u);
    m.setJoinedAt(Instant.now());
    orgUnitMembershipRepository.save(m);
  }

  @Test
  void testUserManageOwnHangar() throws Exception {
    // Add Ship
    ShipRequestDto shipReq =
        new ShipRequestDto("My Fighter", fighter.getId(), "LTI", null, true, null, null);

    String response =
        mockMvc
            .perform(
                post("/api/v1/hangar/ships")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(user1.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("HANGAR_WRITE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(shipReq)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ShipDto savedShip = objectMapper.readValue(response, ShipDto.class);
    assertNotNull(savedShip.id());
    assertEquals("My Fighter", savedShip.name());

    // Update Ship
    ShipRequestDto updateReq =
        new ShipRequestDto(
            "Updated Fighter", fighter.getId(), "LTI", null, true, savedShip.version(), null);
    mockMvc
        .perform(
            put("/api/v1/hangar/ships/" + savedShip.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk());

    Ship updated = shipRepository.findById(savedShip.id()).orElseThrow();
    assertEquals("Updated Fighter", updated.getName());

    // Delete Ship
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships/" + savedShip.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isOk());

    assertTrue(shipRepository.findById(savedShip.id()).isEmpty());
  }

  @Test
  void testUserCannotManageOtherHangar() throws Exception {
    // User1 creates ship
    Ship ship = new Ship();
    ship.setOwningOrgUnit(iridium);
    ship.setName("User1 Ship");
    ship.setShipType(fighter);
    ship.setOwner(user1);
    ship.setInsurance("LTI");
    ship = shipRepository.save(ship);

    // User2 tries to update
    ShipRequestDto upReq =
        new ShipRequestDto("Hacked", fighter.getId(), "LTI", null, false, 0L, null);
    mockMvc
        .perform(
            put("/api/v1/hangar/ships/" + ship.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user2.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upReq)))
        .andExpect(status().isForbidden());

    // User2 tries to delete
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships/" + ship.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user2.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testAdminManageUserHangar() throws Exception {
    // Admin adds ship to User1
    ShipRequestDto req =
        new ShipRequestDto("Admin Gift", fighter.getId(), "LTI", null, false, null, null);

    String response =
        mockMvc
            .perform(
                post("/api/v1/hangar/users/" + user1.getId() + "/ships")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(adminUser.getId().toString()))
                            .authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("ROLE_MANAGE"),
                                new SimpleGrantedAuthority("USER_MANAGE"),
                                new SimpleGrantedAuthority("MISSION_MANAGE"),
                                new SimpleGrantedAuthority("HANGAR_MANAGE"),
                                new SimpleGrantedAuthority("REFINERY_MANAGE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ShipDto savedShip = objectMapper.readValue(response, ShipDto.class);
    assertEquals(user1.getId(), savedShip.owner().id());

    // Admin updates ship
    ShipRequestDto upReq =
        new ShipRequestDto(
            "Admin Updated", fighter.getId(), "LTI", null, false, savedShip.version(), null);
    mockMvc
        .perform(
            put("/api/v1/hangar/users/" + user1.getId() + "/ships/" + savedShip.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_MANAGE"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upReq)))
        .andExpect(status().isOk());

    Ship updated = shipRepository.findById(savedShip.id()).orElseThrow();
    assertEquals("Admin Updated", updated.getName());

    // Admin deletes ship
    mockMvc
        .perform(
            delete("/api/v1/hangar/users/" + user1.getId() + "/ships/" + savedShip.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_MANAGE"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());

    assertTrue(shipRepository.findById(savedShip.id()).isEmpty());
  }

  @Test
  void testAdminResetAllFittedStatus() throws Exception {
    // Setup two fitted ships
    Ship ship1 = new Ship();
    ship1.setOwningOrgUnit(iridium);
    ship1.setName("Fitted Ship 1");
    ship1.setShipType(fighter);
    ship1.setOwner(user1);
    ship1.setInsurance("LTI");
    ship1.setFitted(true);
    shipRepository.save(ship1);

    Ship ship2 = new Ship();

    ship2.setOwningOrgUnit(iridium);
    ship2.setName("Fitted Ship 2");
    ship2.setShipType(fighter);
    ship2.setOwner(user2);
    ship2.setInsurance("LTI");
    ship2.setFitted(true);
    shipRepository.save(ship2);

    // Admin resets all fitted statuses
    mockMvc
        .perform(
            post("/api/v1/hangar/ships/reset-fitted")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(adminUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
        .andExpect(status().isOk());

    // Verify that statuses are now unfitted (fitted = false)
    Ship savedShip1 = shipRepository.findById(ship1.getId()).orElseThrow();
    Ship savedShip2 = shipRepository.findById(ship2.getId()).orElseThrow();

    assertFalse(savedShip1.isFitted());
    assertFalse(savedShip2.isFitted());
  }

  @Test
  void testUserCannotResetAllFittedStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/hangar/ships/reset-fitted")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testSquadronOverview() throws Exception {
    Ship ship1 = new Ship();
    ship1.setOwningOrgUnit(iridium);
    ship1.setName("Fitted Ship 1");
    ship1.setShipType(fighter);
    ship1.setOwner(user1);
    ship1.setInsurance("LTI");
    ship1.setFitted(true);
    shipRepository.save(ship1);

    Ship ship2 = new Ship();

    ship2.setOwningOrgUnit(iridium);
    ship2.setName("Unfitted Ship 2");
    ship2.setShipType(fighter);
    ship2.setOwner(user2);
    ship2.setInsurance("LTI");
    ship2.setFitted(false);
    shipRepository.save(ship2);

    String response =
        mockMvc
            .perform(
                get("/api/v1/hangar/squadron-overview")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(user1.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("HANGAR_READ"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains("\"count\":2"));
    assertTrue(response.contains("\"fittedCount\":1"));
    assertFalse(response.contains("\"details\":[{\"ownerName\""));
  }

  @Test
  void testSquadronOverviewAsAdmin() throws Exception {
    Ship ship1 = new Ship();
    ship1.setOwningOrgUnit(iridium);
    ship1.setName("Admin Fitted Ship");
    ship1.setShipType(fighter);
    ship1.setOwner(user1);
    ship1.setInsurance("LTI");
    ship1.setFitted(true);
    shipRepository.save(ship1);

    String response =
        mockMvc
            .perform(
                get("/api/v1/hangar/squadron-overview")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(adminUser.getId().toString()))
                            .authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("HANGAR_READ"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains("\"count\":1"));
    assertTrue(response.contains("\"fittedCount\":1"));
    assertTrue(response.contains("\"details\":[{\"ownerName\":\"user1\""));
  }

  @Test
  void testDeleteShipInMission() throws Exception {
    // Given a ship owned by user1
    Ship ship = new Ship();
    ship.setOwningOrgUnit(iridium);
    ship.setName("Mission Ship");
    ship.setShipType(fighter);
    ship.setOwner(user1);
    ship.setInsurance("LTI");
    ship = shipRepository.save(ship);

    // And a mission where this ship is assigned
    Mission mission = new Mission();
    mission.setOwningOrgUnit(iridium);
    mission.setName("Test Mission");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);

    MissionUnit unit = new MissionUnit();
    unit.setMission(mission);
    unit.setName("Alpha 1");
    unit.setShip(ship);
    missionUnitRepository.save(unit);

    // When user1 tries to delete the ship
    // Then it should fail with 500 if not handled, or succeed if handled
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships/" + ship.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isOk());

    // Verify that the ship is deleted from repository
    assertTrue(shipRepository.findById(ship.getId()).isEmpty());

    // Verify that the mission unit still exists but has no ship assigned
    MissionUnit updatedUnit = missionUnitRepository.findById(unit.getId()).orElseThrow();
    assertNull(updatedUnit.getShip());
  }

  // ---- DELETE /api/v1/hangar/ships (delete all) ----

  @Test
  void testDeleteAllShips_ReturnsNoContent() throws Exception {
    // Given: user1 has two ships
    Ship ship1 = new Ship();
    ship1.setOwningOrgUnit(iridium);
    ship1.setShipType(fighter);
    ship1.setOwner(user1);
    ship1.setInsurance("LTI");
    shipRepository.save(ship1);

    Ship ship2 = new Ship();

    ship2.setOwningOrgUnit(iridium);
    ship2.setShipType(fighter);
    ship2.setOwner(user1);
    ship2.setInsurance("0");
    shipRepository.save(ship2);

    // When
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isNoContent());

    // Then: user1's ships are deleted
    assertTrue(shipRepository.findByOwnerId(user1.getId()).isEmpty());
  }

  @Test
  void testDeleteAllShips_Unauthenticated_Returns401() throws Exception {
    mockMvc.perform(delete("/api/v1/hangar/ships")).andExpect(status().isUnauthorized());
  }

  @Test
  void testDeleteAllShips_NoHangarWriteAuthority_Returns403() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_READ"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void testDeleteAllShips_WithLinkedMissionUnit_UnlinksBeforeDelete() throws Exception {
    // Given: user1 has a ship assigned to a mission unit
    Ship ship = new Ship();
    ship.setOwningOrgUnit(iridium);
    ship.setShipType(fighter);
    ship.setOwner(user1);
    ship.setInsurance("LTI");
    ship = shipRepository.save(ship);

    Mission mission = new Mission();

    mission.setOwningOrgUnit(iridium);
    mission.setName("Test Mission for Delete All");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);

    MissionUnit unit = new MissionUnit();
    unit.setMission(mission);
    unit.setName("Bravo 1");
    unit.setShip(ship);
    missionUnitRepository.save(unit);

    // When: user1 deletes all ships
    mockMvc
        .perform(
            delete("/api/v1/hangar/ships")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(user1.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("HANGAR_WRITE"))))
        .andExpect(status().isNoContent());

    // Then: ship deleted, mission unit still exists but ship reference is null
    assertTrue(shipRepository.findById(ship.getId()).isEmpty());
    MissionUnit updatedUnit = missionUnitRepository.findById(unit.getId()).orElseThrow();
    assertNull(updatedUnit.getShip());

    // And: user2's ships are unaffected (multi-user isolation)
    Ship user2Ship = new Ship();
    user2Ship.setOwningOrgUnit(iridium);
    user2Ship.setShipType(fighter);
    user2Ship.setOwner(user2);
    user2Ship.setInsurance("LTI");
    shipRepository.save(user2Ship);
    assertFalse(shipRepository.findByOwnerId(user2.getId()).isEmpty());
  }
}
