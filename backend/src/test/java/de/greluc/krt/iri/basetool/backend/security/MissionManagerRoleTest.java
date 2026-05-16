package de.greluc.krt.iri.basetool.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.config.CustomJwtGrantedAuthoritiesConverter;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
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
class MissionManagerRoleTest {

  private MockMvc mockMvc;

  @Autowired private WebApplicationContext context;

  @Autowired private UserRepository userRepository;

  @Autowired private MissionRepository missionRepository;

  private ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  @Autowired private CustomJwtGrantedAuthoritiesConverter converter;

  @MockitoBean private JwtDecoder jwtDecoder;

  private User owner;
  private User otherMember;
  private User managerMember;
  private Mission mission;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    owner = new User();
    owner.setId(UUID.randomUUID());
    owner.setUsername("owner");
    userRepository.save(owner);

    otherMember = new User();
    otherMember.setId(UUID.randomUUID());
    otherMember.setUsername("other");
    userRepository.save(otherMember);

    managerMember = new User();
    managerMember.setId(UUID.randomUUID());
    managerMember.setUsername("manager");
    managerMember.setMissionManager(true);
    userRepository.save(managerMember);

    mission = new Mission();
    mission.setName("Test Mission");
    mission.setOwner(owner);
    mission.setPlannedStartTime(Instant.now().plusSeconds(3600));
    missionRepository.save(mission);
  }

  private String createMissionJson() throws Exception {
    MissionDto dto =
        new MissionDto(
            mission.getId(),
            "Updated Name",
            null,
            null,
            "PLANNED",
            null,
            mission.getPlannedStartTime(),
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
            0);
    return objectMapper.writeValueAsString(dto);
  }

  @Test
  void ownerShouldBeAbleToUpdateMission() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(jwt().jwt(builder -> builder.subject(owner.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionJson()))
        .andExpect(status().isOk());
  }

  @Test
  void missionManagerShouldBeAbleToUpdateMission() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MISSION_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionJson()))
        .andExpect(status().isOk());
  }

  @Test
  void otherMemberShouldNotBeAbleToUpdateMission() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(jwt().jwt(builder -> builder.subject(otherMember.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionJson()))
        .andExpect(status().isForbidden());
  }

  @Test
  void delegatedManagerShouldBeAbleToUpdateMission() throws Exception {
    mission.getManagers().add(otherMember);
    missionRepository.save(mission);

    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(jwt().jwt(builder -> builder.subject(otherMember.getId().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionJson()))
        .andExpect(status().isOk());
  }

  @Test
  void adminShouldBeAbleToUpdateMission() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createMissionJson()))
        .andExpect(status().isOk());
  }

  @Test
  void missionDtoShouldHaveCanEditTrueForAdmin() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.canEdit")
                .value(true));
  }

  @Test
  void missionDtoShouldHaveCanEditTrueForMissionManager() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/missions/" + mission.getId())
                .with(
                    jwt()
                        .authorities(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_MISSION_MANAGER"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.canEdit")
                .value(true));
  }

  @Test
  void missionDtoShouldHaveCanEditFalseForRegularMember() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/missions/" + mission.getId())
                .with(jwt().jwt(builder -> builder.subject(otherMember.getId().toString()))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.canEdit")
                .value(false));
  }

  @Test
  void converterShouldAddMissionManagerRole() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setUsername("manager_user");
    user.setMissionManager(true);
    userRepository.save(user);
    userRepository.flush();

    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", userId.toString())
            .claim("preferred_username", "manager_user")
            .build();

    java.util.Collection<GrantedAuthority> authorities = converter.convert(jwt);
    org.junit.jupiter.api.Assertions.assertTrue(
        authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MISSION_MANAGER")),
        "Should have ROLE_MISSION_MANAGER");
  }
}
