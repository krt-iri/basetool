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

/** Spring Data repository for Mission. */
@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {

  /**
   * Returns slim {@link MissionReferenceDto}s for every mission in {@code PLANNED} or {@code
   * ACTIVE} status, sorted by planned start. Used to populate mission-picker dropdowns without
   * pulling the full Mission aggregate.
   */
  @Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto(m.id, m.name, m.status, m.plannedStartTime) FROM Mission m WHERE m.status IN ('PLANNED', 'ACTIVE') ORDER BY m.plannedStartTime ASC")
  List<de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto> findAllActiveReference();

  /**
   * Derived Spring-Data query - returns entities matching {@code Id}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findById(UUID id);

  /**
   * Returns the first matching {@code PlannedStartTimeAfterOrderByPlannedStartTimeAsc} (limit 1).
   * Eagerly fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findFirstByPlannedStartTimeAfterOrderByPlannedStartTimeAsc(Instant date);

  /**
   * Returns the first matching {@code
   * PlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc} (limit 1). Eagerly fetches
   * the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Optional<Mission> findFirstByPlannedStartTimeAfterAndIsInternalFalseOrderByPlannedStartTimeAsc(
      Instant date);

  /**
   * Full-text + date-range + status + scope search across missions. Each parameter is optional - a
   * {@code null} cast removes the corresponding clause; the {@code status IN (:status)} list is
   * always applied (pass the full enum set to disable status filtering). Result is sorted by
   * planned start ascending; the {@code @EntityGraph} pre-loads participants and assigned units to
   * avoid N+1 when the caller renders the result list.
   */
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

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"participants", "assignedUnits"})
  Page<Mission> findAll(Pageable pageable);

  /**
   * Paged variant of {@link #searchMissions(String, Instant, Instant, List, Boolean, UUID)} - same
   * filter contract; sorting is delegated to {@link Pageable} so the caller can pick the column.
   */
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

  /**
   * Bulk-reassigns every mission owned by {@code oldUser} to {@code newUser}; used by the
   * user-merge flow so missions are preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Mission m SET m.owner = :newUser WHERE m.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);

  /**
   * Removes the given user from every mission's manager set via direct delete on the join table.
   * Native query because Hibernate cannot bulk-delete a {@code @ManyToMany} association directly -
   * JPQL would require loading every mission first.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      value = "DELETE FROM mission_managers WHERE user_id = :userId",
      nativeQuery = true)
  void removeManager(
      @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);
}
