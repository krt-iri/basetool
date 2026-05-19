package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Ship;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Ship. */
@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {

  /**
   * Flips the {@code fitted} flag back to {@code false} on every ship; used by the fleet-import
   * flow as the first step before re-applying the freshly imported fitted set. {@code
   * clearAutomatically = true} flushes the persistence context so subsequent saves in the same
   * transaction do not collide with stale {@code @Version} state.
   */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE Ship s SET s.fitted = false")
  void resetAllFitted();

  /**
   * Squadron-scoped variant of {@link #resetAllFitted()}. Used by the admin/officer "reset fitted"
   * action so a focused-mode caller only wipes the {@code fitted} flag on ships of their own
   * squadron (MULTI_SQUADRON_PLAN.md section 1: Hangar = strict eigene Staffel). {@code
   * owningSquadronId} {@code null} signals admin "all squadrons" mode and falls back to the
   * cross-staffel reset.
   *
   * @param owningSquadronId squadron to scope the reset to, or {@code null} for cross-staffel wipe.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE Ship s SET s.fitted = false WHERE :owningSquadronId IS NULL OR s.owningSquadron.id ="
          + " :owningSquadronId")
  void resetAllFittedScoped(
      @org.springframework.data.repository.query.Param("owningSquadronId") UUID owningSquadronId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * ShipTypeId}.
   */
  boolean existsByShipTypeId(UUID shipTypeId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * LocationId}.
   */
  boolean existsByLocationId(UUID locationId);

  /**
   * Derived Spring-Data check - returns {@code true} iff at least one row matches {@code
   * OwnerIdAndShipTypeId}.
   */
  boolean existsByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns the count of rows matching {@code OwnerIdAndShipTypeId}.
   */
  long countByOwnerIdAndShipTypeId(UUID ownerId, UUID shipTypeId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  List<Ship> findByOwnerId(UUID ownerId);

  /**
   * Derived Spring-Data query - returns entities matching {@code OwnerId}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findByOwnerId(UUID ownerId, Pageable pageable);

  /**
   * Lists every entity. Overridden here to attach an {@code @EntityGraph}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @Override
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  Page<Ship> findAll(Pageable pageable);

  /**
   * Multi-tenant variant of {@link #findAll(Pageable)}: returns every ship whose owning squadron
   * matches {@code owningSquadronId}, or every ship when {@code owningSquadronId} is {@code null}
   * (admin "all squadrons" mode). Eagerly fetches {@code shipType}, {@code location} and {@code
   * owner} via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"shipType", "location", "owner"})
  @Query(
      "SELECT s FROM Ship s WHERE (:owningSquadronId IS NULL OR s.owningSquadron.id ="
          + " :owningSquadronId)")
  Page<Ship> findAllScoped(
      @org.springframework.data.repository.query.Param("owningSquadronId") UUID owningSquadronId,
      Pageable pageable);

  /**
   * Aggregates ships by type for the squadron-overview page: tuple of {@code (shipType, totalCount,
   * fittedCount)} ordered alphabetically by ship-type name. Returns raw {@code Object[]} - the
   * service projects it into the squadron-overview DTO. When {@code owningSquadronId} is {@code
   * null} the aggregation spans every squadron (admin "all squadrons" mode); otherwise the row set
   * is pre-filtered to that squadron's ships only.
   */
  @Query(
      "SELECT s.shipType, COUNT(s), SUM(CASE WHEN s.fitted = true THEN 1 ELSE 0 END) FROM Ship s"
          + " WHERE (:owningSquadronId IS NULL OR s.owningSquadron.id = :owningSquadronId) GROUP BY"
          + " s.shipType ORDER BY s.shipType.name ASC")
  Page<Object[]> countShipsByType(
      @org.springframework.data.repository.query.Param("owningSquadronId") UUID owningSquadronId,
      Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code ShipTypeIn}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"owner", "location"})
  List<Ship> findByShipTypeIn(List<de.greluc.krt.iri.basetool.backend.model.ShipType> shipTypes);

  /**
   * Bulk-reassigns every ship owned by {@code oldUser} to {@code newUser}; used by the user-merge
   * flow so the fleet is preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE Ship s SET s.owner = :newUser WHERE s.owner = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}
