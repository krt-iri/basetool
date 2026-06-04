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

package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
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
class MissionUnitManagementTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;
  @Autowired private MissionRepository missionRepository;
  @Autowired private ShipRepository shipRepository;
  @Autowired private ShipTypeRepository shipTypeRepository;
  @Autowired private MissionService missionService;

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private Mission mission;
  private Ship ship;
  private MissionUnit unit;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officer_unit_mgmt");
    userRepository.save(officerUser);

    ShipType st = new ShipType();
    st.setName("Fighter");
    shipTypeRepository.save(st);

    ship = new Ship();
    ship.setName("Test Ship");
    ship.setOwner(officerUser);
    ship.setShipType(st);
    shipRepository.save(ship);

    mission =
        missionService.createMission(
            new de.greluc.krt.iri.basetool.backend.model.dto.request.CreateMissionRequest(
                "Test Mission Unit Mgmt",
                null,
                null,
                "PLANNED",
                null,
                null,
                null,
                false,
                null,
                null));

    // The ship owner must be a registered participant before the ship can be pinned to a unit.
    missionService.addParticipant(mission.getId(), officerUser.getId());

    mission =
        missionService.addUnitToMission(
            mission.getId(), "Initial Unit", st.getId(), ship.getId(), false, 123.45);
    unit = mission.getAssignedUnits().iterator().next();
  }

  @Test
  void testUpdateUnit_Officer_Allowed() throws Exception {
    String updateJson =
        String.format(
            "{\"name\": \"Updated Unit\", \"shipTypeId\": \"%s\", \"shipId\": \"%s\","
                + " \"highValueUnit\": true, \"frequency\": 111.11}",
            unit.getShipType().getId(), ship.getId());

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId() + "/units/" + unit.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isOk());

    Mission updatedMission = missionRepository.findById(mission.getId()).orElseThrow();
    MissionUnit updatedUnit = updatedMission.getAssignedUnits().iterator().next();

    org.junit.jupiter.api.Assertions.assertEquals("Updated Unit", updatedUnit.getName());
    org.junit.jupiter.api.Assertions.assertTrue(updatedUnit.isHighValueUnit());
    org.junit.jupiter.api.Assertions.assertEquals(111.11, updatedUnit.getFrequency());
  }

  @Test
  void testDeleteUnit_Officer_Allowed() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/missions/" + mission.getId() + "/units/" + unit.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(
                            new SimpleGrantedAuthority("ROLE_OFFICER"),
                            new SimpleGrantedAuthority("USER_MANAGE"),
                            new SimpleGrantedAuthority("MISSION_MANAGE"),
                            new SimpleGrantedAuthority("HANGAR_MANAGE"),
                            new SimpleGrantedAuthority("REFINERY_MANAGE"))))
        .andExpect(status().isOk());

    Mission updatedMission = missionRepository.findById(mission.getId()).orElseThrow();
    assertFalse(
        updatedMission.getAssignedUnits().stream().anyMatch(u -> u.getId().equals(unit.getId())));
  }

  @Test
  void testAddUnit_ShipOwnerNotParticipant_Rejected() {
    // Given: a ship owned by a user who is NOT registered for the mission.
    Ship outsiderShip = shipOwnedByNonParticipant();

    // When / Then: pinning that ship to a new unit is rejected.
    assertThrows(
        IllegalArgumentException.class,
        () ->
            missionService.addUnitToMission(
                mission.getId(),
                "Outsider Unit",
                outsiderShip.getShipType().getId(),
                outsiderShip.getId(),
                false,
                null));
  }

  @Test
  void testAddUnit_ShipOwnerIsParticipant_Allowed() {
    // officerUser is a registered participant (see setUp) and owns `ship`, so the assignment
    // passes.
    Mission updated =
        missionService.addUnitToMission(
            mission.getId(),
            "Participant Unit",
            ship.getShipType().getId(),
            ship.getId(),
            false,
            null);

    assertTrue(
        updated.getAssignedUnits().stream()
            .anyMatch(
                u ->
                    "Participant Unit".equals(u.getName())
                        && u.getShip() != null
                        && ship.getId().equals(u.getShip().getId())));
  }

  @Test
  void testUpdateUnit_ChangeToShipOfNonParticipant_Rejected() {
    // Switching an existing unit to a ship owned by a non-participant is rejected.
    Ship outsiderShip = shipOwnedByNonParticipant();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            missionService.updateMissionUnit(
                mission.getId(),
                unit.getId(),
                "Initial Unit",
                outsiderShip.getShipType().getId(),
                outsiderShip.getId(),
                false,
                null));
  }

  @Test
  void testUpdateUnit_KeepsExistingShipAfterOwnerLeftMission_Allowed() {
    // The unit holds officerUser's ship, assigned while officerUser was a participant. Remove
    // officerUser from the roster so the ship owner is no longer registered, then edit the unit
    // keeping the same ship: the already-assigned ship is grandfathered and the edit still passes.
    UUID participantId =
        missionRepository.findById(mission.getId()).orElseThrow().getParticipants().stream()
            .filter(p -> p.getUser() != null && p.getUser().getId().equals(officerUser.getId()))
            .findFirst()
            .orElseThrow()
            .getId();
    missionService.removeParticipant(mission.getId(), participantId);

    missionService.updateMissionUnit(
        mission.getId(),
        unit.getId(),
        "Renamed Unit",
        ship.getShipType().getId(),
        ship.getId(),
        false,
        222.22);

    MissionUnit reloaded =
        missionRepository.findById(mission.getId()).orElseThrow().getAssignedUnits().stream()
            .filter(u -> u.getId().equals(unit.getId()))
            .findFirst()
            .orElseThrow();
    assertEquals("Renamed Unit", reloaded.getName());
    assertNotNull(reloaded.getShip());
    assertEquals(ship.getId(), reloaded.getShip().getId());
  }

  /**
   * Persists a ship owned by a freshly created user who is not a participant of the mission under
   * test, reusing the {@code ship} field's ship type so the ship-to-type match still holds.
   *
   * @return the saved ship whose owner is absent from the mission roster
   */
  private Ship shipOwnedByNonParticipant() {
    User outsider = new User();
    outsider.setId(UUID.randomUUID());
    outsider.setUsername("outsider_" + UUID.randomUUID());
    userRepository.save(outsider);

    Ship outsiderShip = new Ship();
    outsiderShip.setName("Outsider Ship");
    outsiderShip.setOwner(outsider);
    outsiderShip.setShipType(ship.getShipType());
    return shipRepository.save(outsiderShip);
  }
}
