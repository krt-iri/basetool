package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, UUID> {
    Optional<MissionParticipant> findByMissionIdAndUserId(UUID missionId, UUID userId);

    boolean existsBySquadronId(UUID squadronId);

    boolean existsByDesiredMissionJobTypeId(UUID jobTypeId);
    List<MissionParticipant> findByDesiredMissionJobTypeId(UUID jobTypeId);

    boolean existsByPlannedMissionJobTypeId(UUID jobTypeId);
    List<MissionParticipant> findByPlannedMissionJobTypeId(UUID jobTypeId);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE MissionParticipant mp SET mp.user = null WHERE mp.user.id = :userId")
    void unlinkUser(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}
