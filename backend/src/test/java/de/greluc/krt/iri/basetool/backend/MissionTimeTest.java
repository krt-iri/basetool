package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateParticipantRequest;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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
class MissionTimeTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private MissionRepository missionRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private MissionParticipantRepository missionParticipantRepository;

  @Autowired private MissionService missionService;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @MockitoBean private JwtDecoder jwtDecoder;

  private User officerUser;
  private User memberUser;
  private Mission mission;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    officerUser = new User();
    officerUser.setId(UUID.randomUUID());
    officerUser.setUsername("officerTime");
    userRepository.save(officerUser);

    memberUser = new User();
    memberUser.setId(UUID.randomUUID());
    memberUser.setUsername("memberTime");
    userRepository.save(memberUser);

    mission = new Mission();
    mission.setName("Time Test Mission");
    mission.setStatus("PLANNED");
    mission = missionRepository.save(mission);

    missionService.addParticipant(mission.getId(), memberUser.getId());
  }

  @Test
  void testUpdateMissionTimes_Officer_Allowed() throws Exception {
    Instant start =
        Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    Instant end =
        Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    MissionDto update =
        new MissionDto(
            mission.getId(),
            "Updated Mission",
            null,
            null,
            "PLANNED",
            null,
            start,
            start,
            end,
            end,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            false,
            false,
            0L,
            0L,
            0L,
            0L,
            0,
            0,
            null);

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk());

    Mission saved = missionRepository.findById(mission.getId()).orElseThrow();
    assertEquals("Updated Mission", saved.getName());
    assertEquals(start, saved.getActualStartTime());
    assertEquals(end, saved.getActualEndTime());
  }

  @Test
  void testUpdateMissionTimes_Member_Forbidden() throws Exception {
    MissionDto update =
        new MissionDto(
            mission.getId(),
            "Hacked Mission",
            null,
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            false,
            false,
            0L,
            0L,
            0L,
            0L,
            0,
            0,
            null);

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(memberUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isForbidden());
  }

  private UUID getParticipantId(User user) {
    Mission m = missionRepository.findById(mission.getId()).orElseThrow();
    return m.getParticipants().stream()
        .filter(p -> p.getUser() != null && p.getUser().getId().equals(user.getId()))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  @Test
  void testParticipantTimeConstraints() throws Exception {
    // Set Mission Start Time
    Instant missionStart = Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
    mission.setActualStartTime(missionStart);
    missionRepository.save(mission);

    // Try to set participant start before mission start - now allowed
    UpdateParticipantRequest request =
        new UpdateParticipantRequest(
            null, null, null, null, missionStart.minus(1, ChronoUnit.HOURS), null, null, null, 0L);

    mockMvc
        .perform(
            put("/api/v1/missions/"
                    + mission.getId()
                    + "/participants/"
                    + getParticipantId(memberUser))
                .with(
                    jwt()
                        .jwt(
                            builder ->
                                builder.subject(memberUser.getId().toString()))) // Self update
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  void testParticipantTimeConstraints_Success() throws Exception {
    Instant missionStart = Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS);
    mission.setActualStartTime(missionStart);
    missionRepository.save(mission);

    UpdateParticipantRequest request =
        new UpdateParticipantRequest(
            null, null, null, null, missionStart.plus(1, ChronoUnit.HOURS), null, null, null, 0L);

    mockMvc
        .perform(
            put("/api/v1/missions/"
                    + mission.getId()
                    + "/participants/"
                    + getParticipantId(memberUser))
                .with(jwt().jwt(builder -> builder.subject(memberUser.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    Mission updated = missionRepository.findById(mission.getId()).orElseThrow();
    MissionParticipant p = updated.getParticipants().stream().findFirst().orElseThrow();
    assertEquals(missionStart.plus(1, ChronoUnit.HOURS), p.getStartTime());
  }

  @Test
  void testAutoCloseParticipants() throws Exception {
    // Mission running
    Instant missionStart = Instant.now().minus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    mission.setActualStartTime(missionStart);
    missionRepository.save(mission);

    // Participant active
    MissionParticipant p = mission.getParticipants().stream().findFirst().orElseThrow();
    p.setStartTime(missionStart.plus(1, ChronoUnit.HOURS));
    missionParticipantRepository.save(p);

    // End Mission
    Instant missionEnd = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    MissionDto update =
        new MissionDto(
            mission.getId(),
            mission.getName(),
            null,
            null,
            "COMPLETED",
            null,
            mission.getPlannedStartTime(),
            missionStart,
            null,
            missionEnd,
            false,
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptyList(),
            Collections.emptyList(),
            null,
            null,
            Collections.emptySet(),
            false,
            false,
            0L,
            0L,
            0L,
            0L,
            0,
            0,
            null);

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
        .andExpect(status().isOk());

    // Verify participant closed
    Mission updated = missionRepository.findById(mission.getId()).orElseThrow();
    MissionParticipant pUpdated = updated.getParticipants().stream().findFirst().orElseThrow();
    assertEquals(missionEnd, pUpdated.getEndTime());
  }
}
