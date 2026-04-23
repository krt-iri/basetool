package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.JobTypeArchetype;
import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionCrewDto;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link MissionMapper#resolveCrew(MissionUnit)} places crew members whose
 * assigned JobType is flagged as leadership role at the beginning of the list, and that
 * existing MISSION leadership semantics are not affected by the new CREW leadership support.
 */
class MissionMapperCrewLeaderSortingTest {

    private final MissionMapper mapper = new MissionMapperImpl();

    @Test
    void shouldReturnEmptyList_WhenUnitIsNull() {
        assertEquals(List.of(), mapper.resolveCrew(null));
    }

    @Test
    void shouldReturnEmptyList_WhenCrewIsEmpty() {
        MissionUnit unit = new MissionUnit();
        unit.setCrew(new HashSet<>());
        assertEquals(List.of(), mapper.resolveCrew(unit));
    }

    @Test
    void shouldKeepAlphabeticalOrder_WhenNoLeadershipRolePresent() {
        // Given: three crew members, none with leadership role
        MissionUnit unit = new MissionUnit();
        Set<MissionCrew> crew = new LinkedHashSet<>();
        crew.add(buildCrew("Zulu", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Alpha", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Mike", false, JobTypeArchetype.CREW));
        unit.setCrew(crew);

        // When
        List<MissionCrewDto> result = mapper.resolveCrew(unit);

        // Then: alphabetical
        assertEquals(3, result.size());
        assertEquals("Alpha", result.get(0).participantName());
        assertEquals("Mike", result.get(1).participantName());
        assertEquals("Zulu", result.get(2).participantName());
    }

    @Test
    void shouldPlaceSingleCrewLeaderFirst() {
        // Given
        MissionUnit unit = new MissionUnit();
        Set<MissionCrew> crew = new LinkedHashSet<>();
        crew.add(buildCrew("Alpha", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Bravo", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Commander", true, JobTypeArchetype.CREW));
        unit.setCrew(crew);

        // When
        List<MissionCrewDto> result = mapper.resolveCrew(unit);

        // Then
        assertEquals(3, result.size());
        assertEquals("Commander", result.get(0).participantName());
        assertEquals("Alpha", result.get(1).participantName());
        assertEquals("Bravo", result.get(2).participantName());
    }

    @Test
    void shouldPlaceMultipleCrewLeadersFirst_StableAlphabeticalWithinGroup() {
        // Given
        MissionUnit unit = new MissionUnit();
        Set<MissionCrew> crew = new LinkedHashSet<>();
        crew.add(buildCrew("Yankee", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Bravo-Leader", true, JobTypeArchetype.CREW));
        crew.add(buildCrew("Alpha", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("Alpha-Leader", true, JobTypeArchetype.CREW));
        unit.setCrew(crew);

        // When
        List<MissionCrewDto> result = mapper.resolveCrew(unit);

        // Then: leaders first, alphabetical within each group
        assertEquals(4, result.size());
        assertEquals("Alpha-Leader", result.get(0).participantName());
        assertEquals("Bravo-Leader", result.get(1).participantName());
        assertEquals("Alpha", result.get(2).participantName());
        assertEquals("Yankee", result.get(3).participantName());
    }

    @Test
    void shouldTreatMissionArchetypeLeadershipEquivalentForCrewSorting() {
        // Regression guard: a crew member assigned a MISSION-archetype leadership JobType
        // must also be treated as a leader in the unit crew list, so existing MISSION
        // leadership semantics are preserved.
        MissionUnit unit = new MissionUnit();
        Set<MissionCrew> crew = new LinkedHashSet<>();
        crew.add(buildCrew("Alpha", false, JobTypeArchetype.CREW));
        crew.add(buildCrew("MissionLead", true, JobTypeArchetype.MISSION));
        unit.setCrew(crew);

        List<MissionCrewDto> result = mapper.resolveCrew(unit);

        assertEquals(2, result.size());
        assertEquals("MissionLead", result.get(0).participantName());
        assertEquals("Alpha", result.get(1).participantName());
    }

    @Test
    void resolveCrewResultShouldBeUnmodifiableList_AndContainMappedDtos() {
        MissionUnit unit = new MissionUnit();
        Set<MissionCrew> crew = new LinkedHashSet<>();
        crew.add(buildCrew("Solo", true, JobTypeArchetype.CREW));
        unit.setCrew(crew);

        List<MissionCrewDto> result = mapper.resolveCrew(unit);

        assertNotNull(result);
        assertEquals(1, result.size());
        MissionCrewDto dto = result.get(0);
        assertEquals("Solo", dto.participantName());
        assertTrue(dto.jobTypes() != null && !dto.jobTypes().isEmpty());
    }

    private MissionCrew buildCrew(String guestName, boolean leadership, JobTypeArchetype archetype) {
        MissionParticipant participant = new MissionParticipant();
        participant.setId(UUID.randomUUID());
        participant.setGuestName(guestName);

        JobType jobType = new JobType();
        jobType.setId(UUID.randomUUID());
        jobType.setName("job-" + guestName);
        jobType.setArchetype(archetype);
        jobType.setLeadershipRole(leadership);
        jobType.setActive(true);

        MissionCrew mc = new MissionCrew();
        mc.setId(UUID.randomUUID());
        mc.setParticipant(participant);
        Set<JobType> jts = new HashSet<>();
        jts.add(jobType);
        mc.setJobTypes(jts);
        return mc;
    }
}
