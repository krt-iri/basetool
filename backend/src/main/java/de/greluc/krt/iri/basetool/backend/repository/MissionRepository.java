package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {

  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto(m.id, m.name, m.status, m.plannedStartTime) FROM Mission m WHERE m.status IN ('PLANNED', 'ACTIVE') ORDER BY m.plannedStartTime ASC")
  List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> findAllActiveReference();

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findById(UUID id);

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(Instant date);

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findFirstByPlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc(
      Instant date);

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  @Query(
      "SELECT m FROM Mission m WHERE "
          + "(CAST(:query AS string) IS NULL OR m.name ILIKE CONCAT('%', CAST(:query AS string), '%') OR CAST(m.description AS string) ILIKE CONCAT('%', CAST(:query AS string), '%')) "
          + "AND (CAST(:start AS timestamp) IS NULL OR m.plannedStartTime >= :start) "
          + "AND (CAST(:end AS timestamp) IS NULL OR m.plannedStartTime <= :end) "
          + "AND (m.status IN (:status)) "
          + "AND (:isInternal IS NULL OR m.isInternal = :isInternal) "
          + "AND (CAST(:operationId AS uuid) IS NULL OR m.operation.id = :operationId) "
          + "ORDER BY m.plannedStartTime ASC")
  List<Mission> searchMissions(
      @Param("query") String query,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("status") List<String> status,
      @Param("isInternal") Boolean isInternal,
      @Param("operationId") UUID operationId);

  @Override
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  List<Mission> findAll();

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Page<Mission> findAll(Pageable pageable);

  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  @Query(
      "SELECT m FROM Mission m WHERE "
          + "(CAST(:query AS string) IS NULL OR m.name ILIKE CONCAT('%', CAST(:query AS string), '%') OR CAST(m.description AS string) ILIKE CONCAT('%', CAST(:query AS string), '%')) "
          + "AND (CAST(:start AS timestamp) IS NULL OR m.plannedStartTime >= :start) "
          + "AND (CAST(:end AS timestamp) IS NULL OR m.plannedStartTime <= :end) "
          + "AND (m.status IN (:status)) "
          + "AND (:isInternal IS NULL OR m.isInternal = :isInternal) "
          + "AND (CAST(:operationId AS uuid) IS NULL OR m.operation.id = :operationId)")
  Page<Mission> searchMissions(
      @Param("query") String query,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("status") List<String> status,
      @Param("isInternal") Boolean isInternal,
      @Param("operationId") UUID operationId,
      Pageable pageable);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Mission m SET m.owner = :newUser WHERE m.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM mission_managers WHERE user_id = :userId",
      nativeQuery = true)
  void removeManager(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}
