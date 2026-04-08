package de.greluc.krt.iri.basetool.backend.security;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.util.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionManagerJobTypeTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private JobTypeRepository jobTypeRepository;
    
    @Autowired
    private MissionParticipantRepository participantRepository;

    @Autowired
    private MissionUnitRepository missionUnitRepository;

    @Autowired
    private ShipTypeRepository shipTypeRepository;

    private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private User manager;
    private Mission mission;
    private JobType missionJobType;
    private JobType crewJobType;
    private MissionParticipant participant;
    private MissionUnit unit;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        manager = new User();
        manager.setId(UUID.randomUUID());
        manager.setUsername("manager");
        manager.setMissionManager(true);
        userRepository.save(manager);

        missionJobType = new JobType();
        missionJobType.setName("Test Mission Job");
        missionJobType.setArchetype(JobTypeArchetype.MISSION);
        missionJobType.setActive(true);
        jobTypeRepository.save(missionJobType);

        crewJobType = new JobType();
        crewJobType.setName("Test Crew Job");
        crewJobType.setArchetype(JobTypeArchetype.CREW);
        crewJobType.setActive(true);
        jobTypeRepository.save(crewJobType);

        mission = new Mission();
        mission.setName("Test Mission");
        mission.setOwner(manager);
        mission.setPlannedStartTime(Instant.now().plusSeconds(3600));
        missionRepository.save(mission);
        
        participant = new MissionParticipant();
        participant.setMission(mission);
        participant.setUser(manager);
        participantRepository.save(participant);
        
        mission.getParticipants().add(participant);
        
        ShipType st = new ShipType();
        st.setName("Test Ship");
        shipTypeRepository.save(st);
        
        unit = new MissionUnit();
        unit.setName("Unit 1");
        unit.setMission(mission);
        unit.setShipType(st);
        missionUnitRepository.save(unit);
        
        mission.getAssignedUnits().add(unit);
        missionRepository.save(mission);
    }

    @Test
    void missionManagerShouldBeAbleToAssignJobType() throws Exception {
        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("plannedMissionJobTypeId", missionJobType.getId().toString());
        updateRequest.put("version", participant.getVersion());

        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + participant.getId())
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MISSION_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());
    }

    @Test
    void missionManagerShouldBeAbleToAddCrew() throws Exception {
        Map<String, Object> addCrewRequest = new HashMap<>();
        addCrewRequest.put("participantId", participant.getId().toString());
        addCrewRequest.put("jobTypeIds", List.of(crewJobType.getId().toString()));

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/units/" + unit.getId() + "/crew")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MISSION_MANAGER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addCrewRequest)))
                .andExpect(status().isOk());
    }
}
