/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.profit.basetool.backend.model.*;
import de.greluc.krt.profit.basetool.backend.model.Squadron;
import de.greluc.krt.profit.basetool.backend.model.dto.ShipDto;
import de.greluc.krt.profit.basetool.backend.model.dto.ShipRequestDto;
import de.greluc.krt.profit.basetool.backend.repository.*;
import de.greluc.krt.profit.basetool.backend.repository.SquadronRepository;
import java.math.BigDecimal;
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
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeatureExpansionTest {

  @Autowired private SquadronRepository squadronRepository;

  private Squadron iridium;

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private LocationRepository locationRepository;

  @Autowired private ShipRepository shipRepository;

  @Autowired private ShipTypeRepository shipTypeRepository;

  @Autowired private MissionRepository missionRepository;

  @Autowired private MissionParticipantRepository missionParticipantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private OrgUnitMembershipRepository orgUnitMembershipRepository;

  @Autowired private ManufacturerRepository manufacturerRepository;

  @Autowired private StarSystemRepository starSystemRepository;

  private final JsonMapper objectMapper = JsonMapper.builder().build();

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private User normalUser;
  private User otherUser;
  private ShipType fighter;
  private Location stanton;

  @BeforeEach
  void setUp() {
    iridium = squadronRepository.findById(Squadron.IRIDIUM_ID).orElseThrow();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officerExp");
    userRepository.save(officerUser);
    saveIridiumMembership(officerUser);

    normalUser = new User();
    normalUser.setId(UUID.randomUUID());
    normalUser.setUsername("normalExp");
    userRepository.save(normalUser);
    saveIridiumMembership(normalUser);

    otherUser = new User();
    otherUser.setId(UUID.randomUUID());
    otherUser.setUsername("otherExp");
    userRepository.save(otherUser);
    saveIridiumMembership(otherUser);

    Manufacturer aegis = new Manufacturer();
    aegis.setName("Aegis");
    aegis.setAbbreviation("AGS");
    manufacturerRepository.save(aegis);

    fighter = new ShipType();
    fighter.setName("FighterExp");
    fighter.setManufacturer(aegis);
    fighter = shipTypeRepository.save(fighter);

    StarSystem stantonSys = new StarSystem();
    stantonSys.setName("Stanton System");
    stantonSys = starSystemRepository.save(stantonSys);

    stanton = new Location();
    stanton.setName("Stanton");
    stanton = locationRepository.save(stanton);
  }

  /**
   * Post-R9 D3 (V101): the user's home Staffel lives in org_unit_membership — anchoring the fixture
   * to IRIDIUM via a membership row is the only way for the owner resolver to find a Staffel link.
   */
  private void saveIridiumMembership(User u) {
    OrgUnitMembership m = new OrgUnitMembership();
    m.setId(new OrgUnitMembershipId(u.getId(), Squadron.IRIDIUM_ID));
    m.setUser(u);
    m.setJoinedAt(Instant.now());
    orgUnitMembershipRepository.save(m);
  }

  @Test
  void testLocationCrud_Officer_Forbidden() throws Exception {
    // Seed a Location directly via the repository so the PUT/DELETE paths have a
    // real id to target. The whole point of this test is to verify that an
    // Officer JWT is rejected by the controller layer on every CRUD verb after
    // Phase 4 — none of these calls are allowed to mutate the seeded row.
    Location seeded = new Location();
    seeded.setName("Terra");
    seeded = locationRepository.save(seeded);
    locationRepository.flush();
    UUID seededId = seeded.getId();
    long countBefore = locationRepository.count();

    // POST as Officer → 403, nothing created.
    Location toCreate = new Location();
    toCreate.setName("Terra Prime");
    mockMvc
        .perform(
            post("/api/v1/locations")
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(toCreate)))
        .andExpect(status().isForbidden());
    assertEquals(countBefore, locationRepository.count());

    // PUT as Officer → 403, seeded row stays "Terra". Build the request body as
    // a fresh detached entity so Hibernate dirty-checking on the managed
    // {@code seeded} does NOT flush the renamed value into the DB before the
    // post-PUT assertion (which would silently mask a forbidden bypass).
    Location updatePayload = new Location();
    updatePayload.setId(seededId);
    updatePayload.setName("Terra Prime");
    mockMvc
        .perform(
            put("/api/v1/locations/" + seededId)
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatePayload)))
        .andExpect(status().isForbidden());
    Location afterPut = locationRepository.findById(seededId).orElseThrow();
    assertEquals("Terra", afterPut.getName());

    // DELETE as Officer → 403, row still present.
    mockMvc
        .perform(
            delete("/api/v1/locations/" + seededId)
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"))))
        .andExpect(status().isForbidden());
    assertTrue(locationRepository.findById(seededId).isPresent());
  }

  @Test
  void testShipWithLocation() throws Exception {
    ShipRequestDto req =
        new ShipRequestDto(
            "Located Ship", fighter.getId(), "LTI", stanton.getId(), false, null, null);

    // Use HangarController to add ship
    String response =
        mockMvc
            .perform(
                post("/api/v1/hangar/ships")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(normalUser.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("HANGAR_WRITE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ShipDto savedShip = objectMapper.readValue(response, ShipDto.class);
    assertNotNull(savedShip.location());
    assertEquals(stanton.getId(), savedShip.location().id());
  }

  @Test
  void testSubMission() throws Exception {
    Mission parent = new Mission();
    parent.setOwningOrgUnit(iridium);
    parent.setName("Parent Mission");
    parent = missionRepository.save(parent);

    String subJson =
        String.format("{\"name\": \"Sub Mission\", \"status\": \"PLANNED\", \"version\": 0}");

    String response =
        mockMvc
            .perform(
                post("/api/v1/missions/" + parent.getId() + "/sub-missions")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(officerUser.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(subJson))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    de.greluc.krt.profit.basetool.backend.model.dto.MissionDto savedSub =
        objectMapper.readValue(
            response, de.greluc.krt.profit.basetool.backend.model.dto.MissionDto.class);
    // Note: Response might not show parent depending on JsonIgnore.
    // Check DB
    Mission fromDb = missionRepository.findById(savedSub.id()).orElseThrow();
    assertEquals(parent.getId(), fromDb.getParent().getId());

    // Check parent's subMissions
    // Since we are in same transaction and JPA caching, refresh might be needed, or trust the test
    // context.
    // But subMissions list on parent side is not automatically updated in memory unless we add it
    // or refresh.
    // The service added it to parent? In addSubMission: "subMission.setParent(parent); return
    // missionRepository.save(subMission);"
    // It didn't add to parent's list explicitly in Java object unless we reload.

    // Reload parent
    // EntityManager clear/refresh is tricky in @Transactional test without TestEntityManager.
    // But let's check basic link.
  }

  @Test
  void testMissionFinance() throws Exception {
    Mission mission = new Mission();
    mission.setOwningOrgUnit(iridium);
    mission.setName("Finance Mission");
    mission = missionRepository.save(mission);

    MissionParticipant participant = new MissionParticipant();
    participant.setMission(mission);
    participant.setUser(normalUser);
    participant = missionParticipantRepository.save(participant);

    de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto req =
        new de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto(
            mission.getId(),
            participant.getId(),
            "Fuel",
            FinanceType.EXPENSE,
            new BigDecimal("50.00"));

    String response =
        mockMvc
            .perform(
                post("/api/v1/finance-entries")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(normalUser.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryDto entryDto =
        objectMapper.readValue(
            response, de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryDto.class);
    assertEquals(new BigDecimal("50.00"), entryDto.amount());
    assertEquals(FinanceType.EXPENSE, entryDto.type());

    // Remove
    mockMvc
        .perform(
            delete("/api/v1/finance-entries/" + entryDto.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(normalUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void testMissionFinance_OtherUser_Forbidden() throws Exception {
    Mission mission = new Mission();
    mission.setOwningOrgUnit(iridium);
    mission.setName("Finance Mission 2");
    mission = missionRepository.save(mission);

    MissionParticipant participant = new MissionParticipant();
    participant.setMission(mission);
    participant.setUser(normalUser);
    participant = missionParticipantRepository.save(participant);

    MissionParticipant otherParticipant = new MissionParticipant();
    otherParticipant.setMission(mission);
    otherParticipant.setUser(otherUser);
    otherParticipant = missionParticipantRepository.save(otherParticipant);

    de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto req =
        new de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto(
            mission.getId(),
            participant.getId(),
            "Fuel",
            FinanceType.EXPENSE,
            new BigDecimal("50.00"));

    // otherUser is not a participant of this mission, so they should get Forbidden when trying to
    // create an entry for normalUser, or even themselves.
    // Actually, the check says "must be a registered participant of this mission".
    // Wait, creating entry has check? The `createEntry` logic doesn't explicitly check if the
    // creator is a participant! It just assigns the `dto.userId()`.
    // The requirements say "alle nutzer (auch nicht angemeldete) können neue einnahmen und ausgaben
    // erfassen."
    // So they CAN create! But they cannot update/delete unless it's theirs OR they are admins.

    // Let's create an entry with otherUser for otherUser, then try to update it with normalUser.
    de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto reqOther =
        new de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryCreateDto(
            mission.getId(),
            otherParticipant.getId(),
            "Snacks",
            FinanceType.EXPENSE,
            new BigDecimal("10.00"));

    String response =
        mockMvc
            .perform(
                post("/api/v1/finance-entries")
                    .with(
                        jwt()
                            .jwt(builder -> builder.subject(otherUser.getId().toString()))
                            .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(reqOther)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryDto entryDto =
        objectMapper.readValue(
            response, de.greluc.krt.profit.basetool.backend.model.dto.MissionFinanceEntryDto.class);

    // Try to delete with normalUser (who is a participant, but not the owner) -> Forbidden
    mockMvc
        .perform(
            delete("/api/v1/finance-entries/" + entryDto.id())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(normalUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER"))))
        .andExpect(status().isForbidden());
  }
}
