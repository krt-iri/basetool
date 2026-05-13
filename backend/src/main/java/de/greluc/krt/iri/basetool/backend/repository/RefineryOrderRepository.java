package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Refinery Order. */
@Repository
public interface RefineryOrderRepository extends JpaRepository<RefineryOrder, UUID> {
  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * LocationId}.
   */
  boolean existsByLocationId(UUID locationId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  List<RefineryOrder> findByMissionId(UUID missionId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdAndOwnerId}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  List<RefineryOrder> findByMissionIdAndOwnerId(UUID missionId, UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  List<RefineryOrder> findByMissionIdIn(List<UUID> missionIds);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  List<RefineryOrder> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  Page<RefineryOrder> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerIdAndStatusIn}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  Page<RefineryOrder> findByOwnerIdAndStatusIn(
      UUID ownerId,
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  Page<RefineryOrder> findByStatusIn(
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
  Page<RefineryOrder> findAll(Pageable pageable);

  /**
   * Bulk-clears the {@code mission} reference on every refinery order tied to one of the given
   * missions so the orders survive a mission delete as unassigned (see CLAUDE.md "Operation delete
   * keeps missions intact" rule for the broader pattern).
   */
  @Modifying
  @Query("UPDATE RefineryOrder r SET r.mission = null WHERE r.mission.id IN :missionIds")
  void unlinkMissions(@Param("missionIds") List<UUID> missionIds);

  /**
   * Bulk-reassigns every refinery order owned by {@code oldUser} to {@code newUser}; used by the
   * user-merge flow so refinery history is preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE RefineryOrder r SET r.owner = :newUser WHERE r.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}
