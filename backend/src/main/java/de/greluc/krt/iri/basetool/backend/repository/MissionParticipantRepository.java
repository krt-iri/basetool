package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for Mission Participant. */
public interface MissionParticipantRepository extends JpaRepository<MissionParticipant, UUID> {
  /** Derived Spring-Data query - returns entities matching {@code MissionIdAndUserId}. */
  Optional<MissionParticipant> findByMissionIdAndUserId(UUID missionId, UUID userId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * SquadronId}.
   */
  boolean existsBySquadronId(UUID squadronId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * DesiredMissionJobTypeId}.
   */
  boolean existsByDesiredMissionJobTypeId(UUID jobTypeId);

  /** Derived Spring-Data query - returns entities matching {@code DesiredMissionJobTypeId}. */
  List<MissionParticipant> findByDesiredMissionJobTypeId(UUID jobTypeId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * PlannedMissionJobTypeId}.
   */
  boolean existsByPlannedMissionJobTypeId(UUID jobTypeId);

  /** Derived Spring-Data query - returns entities matching {@code PlannedMissionJobTypeId}. */
  List<MissionParticipant> findByPlannedMissionJobTypeId(UUID jobTypeId);

  /**
   * Bulk-clears the {@code user} reference on every mission participant linked to the given user;
   * used by the user-delete flow so mission history (guest name, status) survives but the personal
   * link is removed.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE MissionParticipant mp SET mp.user = null WHERE mp.user.id = :userId")
  void unlinkUser(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}
