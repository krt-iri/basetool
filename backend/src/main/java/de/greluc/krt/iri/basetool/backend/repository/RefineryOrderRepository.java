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
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionId(UUID missionId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdAndOwnerId}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionIdAndOwnerId(UUID missionId, UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code MissionIdIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByMissionIdIn(List<UUID> missionIds);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  List<RefineryOrder> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerIdAndStatusIn}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByOwnerIdAndStatusIn(
      UUID ownerId,
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code StatusIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findByStatusIn(
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  Page<RefineryOrder> findAll(Pageable pageable);

  /**
   * Multi-tenant variant of {@link #findAll(Pageable)}: returns every refinery order whose owning
   * squadron matches {@code owningSquadronId}, or every order when {@code owningSquadronId} is
   * {@code null} (admin "all squadrons" mode). Refinery is a strict-staffel aggregate - there is no
   * cross-squadron escape clause like Mission's {@code is_internal = false}.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  @Query(
      "SELECT r FROM RefineryOrder r WHERE ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND r.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND r.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<RefineryOrder> findAllScoped(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Scoped variant of {@link #findByStatusIn(List, Pageable)}: filters by org-unit scope using the
   * standard {@code isAdminAllScope} / {@code activeOrgUnitId} / {@code memberOrgUnitIds} triple.
   * Used by the refinery list page when an admin selected an active squadron via the switcher and
   * by non-admin users to see the union of their org-unit refinery orders.
   */
  @EntityGraph(
      attributePaths = {"owner", "location", "mission", "refiningMethod", "owningOrgUnit"})
  @Query(
      "SELECT r FROM RefineryOrder r WHERE r.status IN :statuses AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND r.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND r.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<RefineryOrder> findByStatusInScoped(
      @Param("statuses")
          List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

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
