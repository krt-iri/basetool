package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private MissionRepository missionRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void testMissionConcurrency() {
        // 1. Create and save a mission
        Mission mission = new Mission();
        mission.setName("Concurrency Mission");
        mission.setStatus("PLANNED");
        mission = missionRepository.save(mission);
        
        // 2. Load the mission twice (simulating two users)
        Mission missionUser1 = missionRepository.findById(mission.getId()).orElseThrow();
        Mission missionUser2 = missionRepository.findById(mission.getId()).orElseThrow();
        
        // 3. User 1 updates and saves
        missionUser1.setName("Updated by User 1");
        missionRepository.save(missionUser1);
        
        // 4. User 2 updates and tries to save (should fail due to optimistic locking)
        missionUser2.setName("Updated by User 2");
        
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            missionRepository.save(missionUser2);
        });
    }
}
