package de.greluc.krt.iri.basetool.backend;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class MissionDataLeakTest {

  @Autowired private WebApplicationContext context;

  @Autowired private MissionRepository missionRepository;

  @Autowired private UserRepository userRepository;

  private MockMvc mockMvc;
  private Mission publicMission;
  private User testUser;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    testUser = new User();
    testUser.setId(UUID.randomUUID());
    testUser.setUsername("leaked_user");
    testUser.setEmail("secret@leaked.org");
    testUser.setFirstName("Secret");
    testUser.setLastName("User");
    userRepository.save(testUser);

    publicMission = new Mission();
    publicMission.setName("Public Mission");
    publicMission.setStatus("PLANNED");
    publicMission.setIsInternal(false);

    MissionParticipant participant = new MissionParticipant();
    participant.setMission(publicMission);
    participant.setUser(testUser);
    publicMission.getParticipants().add(participant);

    missionRepository.save(publicMission);
  }

  @Test
  void testMissionDetail_Anonymous_ShouldNotLeakInternalUserData() throws Exception {
    mockMvc
        .perform(get("/api/v1/missions/" + publicMission.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.participants[0].user.username").value("leaked_user"))
        .andExpect(jsonPath("$.participants[0].user.email").value(nullValue()))
        .andExpect(jsonPath("$.participants[0].user.firstName").value(nullValue()))
        .andExpect(jsonPath("$.participants[0].user.lastName").value(nullValue()));
  }

  @Test
  void testMissionDetail_Anonymous_ShouldNotLeakInternalMissionData() throws Exception {
    mockMvc
        .perform(get("/api/v1/missions/" + publicMission.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.refineryOrders").isEmpty())
        .andExpect(jsonPath("$.inventoryEntries").isEmpty());
  }
}
