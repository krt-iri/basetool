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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.profit.basetool.backend.model.JobType;
import de.greluc.krt.profit.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.profit.basetool.backend.model.Mission;
import de.greluc.krt.profit.basetool.backend.model.MissionParticipant;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionGuestAccessTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private MissionRepository missionRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JobTypeRepository jobTypeRepository;

  @Autowired
  private de.greluc.krt.profit.basetool.backend.service.GuestParticipantTokenService
      guestParticipantTokenService;

  private User registeredUser;
  private Mission mission;
  private JobType testJobType;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    registeredUser = new User();
    registeredUser.setId(UUID.randomUUID());
    registeredUser.setUsername("regUser");
    registeredUser = userRepository.save(registeredUser);

    testJobType = new JobType();
    testJobType.setName("Pilot");
    testJobType.setArchetype(JobTypeArchetype.MISSION);
    testJobType = jobTypeRepository.save(testJobType);

    mission = new Mission();
    mission.setName("Test Mission");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);
  }

  @Test
  void testUpdateParticipant_Anonymous_OnRegisteredUser_ShouldBeForbidden() throws Exception {
    // Add registered user to mission
    MissionParticipant p = new MissionParticipant();
    p.setMission(mission);
    p.setUser(registeredUser);
    mission.getParticipants().add(p);
    mission = missionRepository.save(mission);

    // Fetch valid participant ID from DB (hibernate might assign one)
    p = mission.getParticipants().iterator().next();

    String updateJson =
        "{\"desiredMissionJobTypeId\": \""
            + testJobType.getId()
            + "\", \"comment\": \"Hacked\", \"version\": 0}";

    // Anonymous request (no with(jwt()))
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isForbidden());
  }

  @Test
  void testDeleteParticipant_Anonymous_OnRegisteredUser_ShouldBeForbidden() throws Exception {
    // Add registered user to mission
    MissionParticipant p = new MissionParticipant();
    p.setMission(mission);
    p.setUser(registeredUser);
    mission.getParticipants().add(p);
    mission = missionRepository.save(mission);

    p = mission.getParticipants().iterator().next();

    // Anonymous request
    mockMvc
        .perform(delete("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId()))
        .andExpect(status().isForbidden());
  }

  @Test
  void testUpdateParticipant_Anonymous_OnGuestEntry_WithoutToken_ShouldBeForbidden()
      throws Exception {
    // Security audit M1 / REQ-SEC-018: an anonymous caller who merely knows a guest participant's
    // id (exposed in the public roster) may no longer mutate it — without the per-row capability
    // token the guest row is editable only by a mission manager. This is the core M1 fix.
    MissionParticipant p = new MissionParticipant();
    p.setMission(mission);
    p.setGuestName("Guest1");
    p.setGuestEditTokenHash(guestParticipantTokenService.hashToken("the-real-token"));
    mission.getParticipants().add(p);
    mission = missionRepository.save(mission);

    p = mission.getParticipants().iterator().next();

    String updateJson =
        "{\"desiredMissionJobTypeId\": \""
            + testJobType.getId()
            + "\", \"comment\": \"Vandalism\", \"version\": 0}";

    // Anonymous request with NO X-Guest-Edit-Token header → forbidden.
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isForbidden());
  }

  @Test
  void testUpdateParticipant_Anonymous_OnGuestEntry_WithValidToken_ShouldBeAllowed()
      throws Exception {
    // Option B self-edit: the anonymous creator who presents the per-row capability token minted at
    // sign-up CAN edit/withdraw their own guest sign-up (security audit M1 / REQ-SEC-018).
    String token = guestParticipantTokenService.generateToken();
    MissionParticipant p = new MissionParticipant();
    p.setMission(mission);
    p.setGuestName("Guest1");
    p.setGuestEditTokenHash(guestParticipantTokenService.hashToken(token));
    mission.getParticipants().add(p);
    mission = missionRepository.save(mission);

    p = mission.getParticipants().iterator().next();

    String updateJson =
        "{\"desiredMissionJobTypeId\": \""
            + testJobType.getId()
            + "\", \"comment\": \"Guest Update\", \"version\": 0}";

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .header("X-Guest-Edit-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isOk());
  }
}
