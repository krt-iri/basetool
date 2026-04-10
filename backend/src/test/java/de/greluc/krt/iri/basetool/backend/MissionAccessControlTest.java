package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.JobTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MissionAccessControlTest {

    @Autowired
    private WebApplicationContext context;
    
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionParticipantRepository missionParticipantRepository;

    @Autowired
    private JobTypeRepository jobTypeRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User officerUser;
    private User guestUser;
    private JobType testJobType;

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

        guestUser = new User();
        guestUser.setId(UUID.randomUUID());
        guestUser.setUsername("guest1");
        userRepository.save(guestUser);

        testJobType = new JobType();
        testJobType.setName("Test Job");
        testJobType.setArchetype(JobTypeArchetype.MISSION);
        testJobType = jobTypeRepository.save(testJobType);
    }

    @Test
    void testCreateMission_Officer_Allowed() throws Exception {
        String json = "{\"name\": \"Officer Mission\", \"status\": \"PLANNED\", \"version\": 0}";

        mockMvc.perform(post("/api/v1/missions")
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testCreateMission_Unauthenticated_Allowed() throws Exception {
        String json = "{\"name\": \"Anonymous Mission\", \"status\": \"PLANNED\", \"version\": 0}";

        mockMvc.perform(post("/api/v1/missions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
    }

    @Test
    void testGetNextMission_Anonymous_Allowed() throws Exception {
        mockMvc.perform(get("/api/v1/missions/next"))
                .andExpect(status().isNoContent()); // Assuming no missions in test db
    }

    @Test
    void testJoinMission_Guest_Allowed() throws Exception {
        Mission mission = new Mission();
        mission.setName("Open Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/join")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateParticipant_Self_Allowed() throws Exception {
        Mission mission = new Mission();
        mission.setName("Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);
        
        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/join")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))));

        // Fetch mission to get participant ID
        Mission m = missionRepository.findById(mission.getId()).orElseThrow();
        MissionParticipant p = m.getParticipants().stream()
            .filter(mp -> mp.getUser() != null && mp.getUser().getId().equals(guestUser.getId()))
            .findFirst().orElseThrow();

        String updateJson = "{\"desiredMissionJobTypeId\": \"" + testJobType.getId() + "\", \"comment\": \"Ready\", \"version\": 0}";

        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateParticipant_OtherGuest_Forbidden() throws Exception {
        Mission mission = new Mission();
        mission.setName("Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        // guest1 joins
        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/join")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))));
        
        User otherGuest = new User();
        otherGuest.setId(UUID.randomUUID());
        otherGuest.setUsername("guest2");
        userRepository.save(otherGuest);

        // Fetch mission to get participant ID
        Mission m = missionRepository.findById(mission.getId()).orElseThrow();
        MissionParticipant p = m.getParticipants().stream()
            .filter(mp -> mp.getUser() != null && mp.getUser().getId().equals(guestUser.getId()))
            .findFirst().orElseThrow();

        String updateJson = "{\"desiredMissionJobTypeId\": \"" + testJobType.getId() + "\", \"comment\": \"Malicious\", \"version\": 0}";

        // guest-2 tries to update guest-1 -> Now Forbidden
        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .with(jwt().jwt(builder -> builder.subject(otherGuest.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateParticipant_Officer_Allowed() throws Exception {
        Mission mission = new Mission();
        mission.setName("Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        // guest1 joins
        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/join")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))));

        // Fetch mission to get participant ID
        Mission m = missionRepository.findById(mission.getId()).orElseThrow();
        MissionParticipant p = m.getParticipants().stream()
            .filter(mp -> mp.getUser() != null && mp.getUser().getId().equals(guestUser.getId()))
            .findFirst().orElseThrow();

        String updateJson = "{\"desiredMissionJobTypeId\": \"" + testJobType.getId() + "\", \"comment\": \"Approved\", \"version\": 0}";

        // Officer updates guest-1
        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .with(jwt().jwt(builder -> builder.subject(officerUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_OFFICER"), new SimpleGrantedAuthority("USER_MANAGE"), new SimpleGrantedAuthority("MISSION_MANAGE"), new SimpleGrantedAuthority("HANGAR_MANAGE"), new SimpleGrantedAuthority("REFINERY_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk());
    }
    @Test
    void testUpdateParticipant_AllFields() throws Exception {
        Mission mission = new Mission();
        mission.setName("Mission Full");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);
        
        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/join")
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))));

        // Fetch mission to get participant ID
        Mission m = missionRepository.findById(mission.getId()).orElseThrow();
        MissionParticipant p = m.getParticipants().stream()
            .filter(mp -> mp.getUser() != null && mp.getUser().getId().equals(guestUser.getId()))
            .findFirst().orElseThrow();

        String updateJson = String.format(
            "{\"desiredMissionJobTypeId\": \"%s\", \"plannedMissionJobTypeId\": \"%s\", \"comment\": \"Full Update\", \"version\": 0}",
            testJobType.getId(), testJobType.getId()
        );

        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + p.getId())
                .with(jwt().jwt(builder -> builder.subject(guestUser.getId().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_GUEST")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk());
                
        // Verification via Repository
        de.greluc.krt.iri.basetool.backend.model.MissionParticipant participant = missionRepository.findById(mission.getId()).orElseThrow()
            .getParticipants().stream()
            .filter(mp1 -> mp1.getUser().getId().equals(guestUser.getId()))
            .findFirst().orElseThrow();
            
        org.junit.jupiter.api.Assertions.assertEquals(testJobType.getId(), participant.getDesiredMissionJobType().getId());
        org.junit.jupiter.api.Assertions.assertEquals(testJobType.getId(), participant.getPlannedMissionJobType().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Full Update", participant.getComment());
    }
    @Test
    void testSearchMissions_Guest_Default_ShouldSeeOnlyPlannedAndActive() throws Exception {
        missionRepository.deleteAll(); // Ensure clean state for counting

        Mission m1 = new Mission(); m1.setName("M1"); m1.setStatus("PLANNED"); missionRepository.save(m1);
        Mission m2 = new Mission(); m2.setName("M2"); m2.setStatus("ACTIVE"); missionRepository.save(m2);
        Mission m3 = new Mission(); m3.setName("M3"); m3.setStatus("COMPLETED"); missionRepository.save(m3);
        Mission m4 = new Mission(); m4.setName("M4"); m4.setStatus("CANCELLED"); missionRepository.save(m4);

        mockMvc.perform(get("/api/v1/missions/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void testSearchMissions_Guest_ExplicitPast_ShouldBeIgnored() throws Exception {
        missionRepository.deleteAll();

        Mission m1 = new Mission(); m1.setName("M1"); m1.setStatus("COMPLETED"); missionRepository.save(m1);
        Mission m2 = new Mission(); m2.setName("M2"); m2.setStatus("CANCELLED"); missionRepository.save(m2);

        // Guest requests completed/cancelled explicitly -> Should receive empty list (200 OK)
        mockMvc.perform(get("/api/v1/missions/search")
                        .param("status", "COMPLETED")
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void testGetMissionById_Guest_Planned_Allowed() throws Exception {
        Mission m = new Mission(); m.setName("Public"); m.setStatus("PLANNED"); 
        m = missionRepository.save(m);

        mockMvc.perform(get("/api/v1/missions/" + m.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void testGetMissionById_Guest_Completed_Forbidden() throws Exception {
        Mission m = new Mission(); m.setName("Secret"); m.setStatus("COMPLETED"); 
        m = missionRepository.save(m);

        mockMvc.perform(get("/api/v1/missions/" + m.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAddParticipantPublic_Anonymous_Allowed() throws Exception {
        Mission mission = new Mission();
        mission.setName("Public Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        String jsonBody = String.format("{\"guestName\": \"John Doe\", \"comment\": \"I want to join\", \"desiredJobTypeId\": \"%s\", \"squadronId\": \"%s\"}", testJobType.getId(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/missions/" + mission.getId() + "/participants/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdateParticipant_AnonymousGuest_Allowed() throws Exception {
        // Given
        Mission mission = new Mission();
        mission.setName("Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        MissionParticipant participant = new MissionParticipant();
        participant.setMission(mission);
        participant.setUser(null); // Guest
        participant.setGuestName("Guest User");
        participant = missionParticipantRepository.save(participant);
        mission.getParticipants().add(participant);
        missionRepository.save(mission);

        // When/Then
        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + participant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"guestName\": \"New Name\", \"version\": 0}"))
                .andExpect(status().isOk());
    }

    @Test
    void testUpdatePayoutPreference_AnonymousGuest_Allowed() throws Exception {
        // Given
        Mission mission = new Mission();
        mission.setName("Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);

        MissionParticipant participant = new MissionParticipant();
        participant.setMission(mission);
        participant.setUser(null); // Guest
        participant.setGuestName("Guest User");
        participant = missionParticipantRepository.save(participant);
        mission.getParticipants().add(participant);
        missionRepository.save(mission);

        // When/Then
        mockMvc.perform(put("/api/v1/missions/" + mission.getId() + "/participants/" + participant.getId() + "/payout-preference")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preference\": \"PAYOUT\", \"version\": 0}"))
                .andExpect(status().isOk());
    }
}
