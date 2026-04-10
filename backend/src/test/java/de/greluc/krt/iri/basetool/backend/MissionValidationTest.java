package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.AddCrewRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.AddParticipantPublicRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateParticipantRequest;
import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import de.greluc.krt.iri.basetool.backend.service.MissionService;
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

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionValidationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private ShipTypeRepository shipTypeRepository;

    @Autowired
    private MissionService missionService;

    @Autowired
    private JobTypeRepository jobTypeRepository;

    @Autowired
    private SquadronRepository squadronRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User officerUser;
    private Mission mission;
    private MissionUnit missionShip;
    private JobType taskJobType;
    private JobType crewJobType;
    private Squadron testSquadron;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        officerUser = new User();
        officerUser.setId(UUID.randomUUID());
        officerUser.setUsername("officer1");
        userRepository.save(officerUser);

        testSquadron = new Squadron();
        testSquadron.setName("Test Sq");
        testSquadron.setShorthand("TS");
        testSquadron = squadronRepository.save(testSquadron);

        ShipType st = new ShipType();
        st.setName("Fighter");
        shipTypeRepository.save(st);

        Ship ship = new Ship();
        ship.setName("Test Ship");
        ship.setInsurance("10");
        ship.setOwner(officerUser);
        ship.setShipType(st);
        shipRepository.save(ship);

        mission = new Mission();
        mission.setName("Test Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        missionService.addParticipant(mission.getId(), officerUser.getId());
        missionService.addUnitToMission(mission.getId(), "Test Unit", st.getId(), ship.getId(), false, null);
        mission = missionRepository.findById(mission.getId()).orElseThrow();
        missionShip = mission.getAssignedUnits().iterator().next();

        taskJobType = new JobType();
        taskJobType.setName("Task Job");
        taskJobType.setArchetype(JobTypeArchetype.MISSION); // Incorrect archetype for crew
        taskJobType = jobTypeRepository.save(taskJobType);

        crewJobType = new JobType();
        crewJobType.setName("Crew Job");
        crewJobType.setArchetype(JobTypeArchetype.CREW); // Incorrect archetype for participant fields
        crewJobType = jobTypeRepository.save(crewJobType);
    }

    private UUID getParticipantId(User user) {
        Mission m = missionRepository.findById(mission.getId()).orElseThrow();
        return m.getParticipants().stream()
            .filter(p -> p.getUser() != null && p.getUser().getId().equals(user.getId()))
            .findFirst().orElseThrow().getId();
    }

    @Test
    void testAddCrewWithInvalidJobTypeArchetype_ShouldReturn400() throws Exception {
        AddCrewRequest request = new AddCrewRequest(getParticipantId(officerUser), Set.of(taskJobType.getId()));

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/units/" + missionShip.getId() + "/crew")
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // Expecting 400
    }

    @Test
    void testUpdateParticipant_WithCrewArchetypeAsDesired_ShouldReturn400() throws Exception {
        UpdateParticipantRequest request = new UpdateParticipantRequest(
                crewJobType.getId(), null, "Invalid Update", null, null, null, null, null, 0L);

        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + getParticipantId(officerUser))
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateParticipant_WithCrewArchetypeAsPlanned_ShouldReturn400() throws Exception {
        UpdateParticipantRequest request = new UpdateParticipantRequest(
                null, crewJobType.getId(), "Invalid Update", null, null, null, null, null, 0L);

        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + getParticipantId(officerUser))
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddParticipantPublic_GuestNameTaken_ShouldReturn400() throws Exception {
        AddParticipantPublicRequest request = new AddParticipantPublicRequest(
            null, "officer1", null, null, null
        );

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/participants/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAddParticipantPublic_GuestNameUnique_ShouldReturn200() throws Exception {
        AddParticipantPublicRequest request = new AddParticipantPublicRequest(
            null, "UniqueGuestName", taskJobType.getId(), "Comment", testSquadron.getId()
        );

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/participants/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
