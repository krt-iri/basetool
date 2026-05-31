package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Inventory Item. */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

  /**
   * Loads every {@link InventoryItem} together with its {@code material}, {@code location}, {@code
   * user}, {@code jobOrder} and {@code mission} relations in one query - the explicit JPQL plus
   * {@code @EntityGraph} avoids the N+1 the default {@code findAll()} would emit when callers touch
   * any of those fields.
   */
  @EntityGraph(
      attributePaths = {"material", "location", "user", "jobOrder", "mission", "owningOrgUnit"})
  @Query("SELECT i FROM InventoryItem i")
  List<InventoryItem> findAllWithEagerRelationships();

  /**
   * Derived Spring-Data query - returns entities matching {@code User}. Eagerly fetches the
   * configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(
      attributePaths = {"material", "location", "user", "jobOrder", "mission", "owningOrgUnit"})
  Page<InventoryItem> findByUser(User user, Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code MaterialAndPersonalFalse}. */
  Page<InventoryItem> findByMaterialAndPersonalFalse(Material material, Pageable pageable);

  /**
   * Squadron-scoped variant of {@link #findByMaterialAndPersonalFalse(Material, Pageable)}. Used by
   * the per-material drilldown so the Lager-direct path stays strictly staffel-isolated
   * (MULTI_SQUADRON_PLAN.md section 1: Inventory direct view = strict eigene Staffel). {@code
   * owningSquadronId} {@code null} = admin "all squadrons" mode (no filter applied); a non-null id
   * restricts to that squadron.
   */
  @EntityGraph(
      attributePaths = {"material", "location", "user", "jobOrder", "mission", "owningOrgUnit"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.material = :material AND i.personal = false AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND i.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND i.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  Page<InventoryItem> findByMaterialAndPersonalFalseScoped(
      @Param("material") Material material,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /** Derived Spring-Data query - returns entities matching {@code PersonalFalse}. */
  Page<InventoryItem> findByPersonalFalse(Pageable pageable);

  /**
   * Optional multi-filter search across non-personal inventory items. Each filter is gated by a
   * boolean / nullable flag so callers can omit dimensions without building a dynamic query: {@code
   * hasMaterials}, {@code hasJobOrders} and {@code hasMissions} turn the corresponding {@code IN
   * :ids} clause on or off; a {@code null minQuality} skips the quality floor.
   *
   * <p>Multi-tenant: this method is the <em>Lager-View</em> entry point (MULTI_SQUADRON_PLAN.md
   * section 4.4). {@code owningSquadronId} restricts to the caller's squadron stock; {@code null}
   * means admin "all squadrons" mode. Items owned by another squadron NEVER surface here, even if
   * they are linked to a job order - the Job-Order-Kontext is a separate, intentionally ungated
   * lookup path served by {@link #findByJobOrderIdOrdered(UUID)}.
   */
  @EntityGraph(
      attributePaths = {"material", "location", "user", "jobOrder", "mission", "owningOrgUnit"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.personal = false AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND i.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND i.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " ) AND (:hasMaterials = false OR i.material.id IN :materialIds) AND (:minQuality IS"
          + " NULL OR i.quality >= :minQuality) AND (:hasJobOrders = false OR (i.jobOrder IS NOT"
          + " NULL AND i.jobOrder.id IN :jobOrderIds)) AND (:hasMissions = false OR (i.mission IS"
          + " NOT NULL AND i.mission.id IN :missionIds))")
  Page<InventoryItem> findGlobalByFilters(
      @Param("hasMaterials") boolean hasMaterials,
      @Param("materialIds") List<UUID> materialIds,
      @Param("minQuality") Integer minQuality,
      @Param("hasJobOrders") boolean hasJobOrders,
      @Param("jobOrderIds") List<UUID> jobOrderIds,
      @Param("hasMissions") boolean hasMissions,
      @Param("missionIds") List<UUID> missionIds,
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Per-user variant of {@link #findGlobalByFilters} - same optional filter contract, but scoped to
   * the items owned by {@code :user}. Used by the "my inventory" view to enforce isolation at the
   * data layer rather than relying on the controller alone.
   */
  @EntityGraph(
      attributePaths = {"material", "location", "user", "jobOrder", "mission", "owningOrgUnit"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.user = :user AND (:hasMaterials = false OR"
          + " i.material.id IN :materialIds) AND (:minQuality IS NULL OR i.quality >= :minQuality)"
          + " AND (:hasJobOrders = false OR (i.jobOrder IS NOT NULL AND i.jobOrder.id IN"
          + " :jobOrderIds)) AND (:hasMissions = false OR (i.mission IS NOT NULL AND i.mission.id"
          + " IN :missionIds))")
  Page<InventoryItem> findUserByFilters(
      @Param("user") User user,
      @Param("hasMaterials") boolean hasMaterials,
      @Param("materialIds") List<UUID> materialIds,
      @Param("minQuality") Integer minQuality,
      @Param("hasJobOrders") boolean hasJobOrders,
      @Param("jobOrderIds") List<UUID> jobOrderIds,
      @Param("hasMissions") boolean hasMissions,
      @Param("missionIds") List<UUID> missionIds,
      Pageable pageable);

  /**
   * Aggregates non-personal inventory by {@code material}: total amount, plus an amount-weighted
   * mean quality (so 10 units at quality 800 plus 5 units at quality 600 land at {@code (10*800 +
   * 5*600) / 15}). Used by the global "aggregated inventory" view; returns raw {@code Object[]}
   * tuples - the service layer projects them into {@code AggregatedInventoryDto}.
   *
   * <p>Multi-tenant: {@code owningSquadronId} restricts to the caller's squadron stock. {@code
   * null} means admin "all squadrons" mode (aggregated across the whole org).
   */
  @Query(
      "SELECT i.material as material, CASE WHEN SUM(i.amount) > 0 THEN SUM(CAST(i.quality AS"
          + " double) * i.amount) / SUM(i.amount) ELSE 0.0 END as quality, SUM(i.amount) as amount"
          + " FROM InventoryItem i WHERE i.personal = false AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND i.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND i.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " ) GROUP BY i.material")
  Page<Object[]> getAggregatedInventory(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds,
      Pageable pageable);

  /**
   * Derived Spring-Data query - returns entities matching {@code JobOrderIdAndMaterialId}. Eagerly
   * fetches the configured relations via {@code @EntityGraph}.
   */
  @EntityGraph(attributePaths = {"user", "location", "material", "owningOrgUnit"})
  List<InventoryItem> findByJobOrderIdAndMaterialId(UUID jobOrderId, UUID materialId);

  /** Derived Spring-Data query - returns entities matching {@code JobOrderIdOrdered}. */
  @EntityGraph(attributePaths = {"user", "location", "material", "owningOrgUnit"})
  @Query(
      "SELECT i FROM InventoryItem i WHERE i.jobOrder.id = :jobOrderId ORDER BY i.user.username"
          + " ASC, i.location.name ASC, i.material.name ASC, i.quality DESC, i.amount DESC")
  List<InventoryItem> findByJobOrderIdOrdered(@Param("jobOrderId") UUID jobOrderId);

  /**
   * Returns the total {@code amount} of one material assigned to one job-order whose quality meets
   * or exceeds the threshold; {@code 0.0} if there is no matching row. A {@code null} minQuality
   * (Keine) imposes no quality floor — all qualities count. Native query because the {@code
   * COALESCE} + {@code SUM} combination simplifies the null-handling at the call site (the
   * job-order completion check would otherwise need a separate empty/null guard).
   */
  @Query(
      value =
          "SELECT COALESCE(SUM(amount), 0.0) FROM inventory_item "
              + "WHERE material_id = :materialId "
              + "AND job_order_id = :jobOrderId "
              + "AND (:minQuality IS NULL OR quality >= :minQuality)",
      nativeQuery = true)
  Double sumAmountByMaterialAndJobOrderAndMinQuality(
      @Param("materialId") UUID materialId,
      @Param("jobOrderId") UUID jobOrderId,
      @Param("minQuality") Integer minQuality);

  /**
   * Bulk-clears the {@code jobOrder} reference on every inventory item linked to the given
   * job-order so the items survive the job-order's deletion as unassigned stock.
   */
  @Modifying
  @Query("UPDATE InventoryItem i SET i.jobOrder = null WHERE i.jobOrder.id = :jobOrderId")
  void unlinkJobOrder(@Param("jobOrderId") UUID jobOrderId);

  /**
   * Bulk-clears {@code jobOrder} only on items of one specific material under the job-order; used
   * by the handover flow when a single material gets returned. {@code clearAutomatically =
   * flushAutomatically = true} flushes pending changes first and clears the persistence context
   * afterwards so any subsequent {@code repository.save(entity)} call in the same transaction does
   * not collide with a stale {@code @Version} - see the loop-bulk-update note in CLAUDE.md.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE InventoryItem i SET i.jobOrder = null WHERE i.jobOrder.id = :jobOrderId AND"
          + " i.material.id = :materialId")
  void unlinkJobOrderMaterial(
      @Param("jobOrderId") UUID jobOrderId, @Param("materialId") UUID materialId);

  /**
   * Bulk-clears the {@code mission} reference on every inventory item belonging to one of the given
   * missions so the items survive a mission delete as unassigned stock.
   */
  @Modifying
  @Query("UPDATE InventoryItem i SET i.mission = null WHERE i.mission.id IN :missionIds")
  void unlinkMissions(@Param("missionIds") List<UUID> missionIds);

  /**
   * Finds inventory items whose seven natural-key dimensions ({@code user}, {@code material},
   * {@code location}, {@code quality}, {@code mission}, {@code jobOrder}, {@code personal}) all
   * match - including the case where the mission, job-order or personal flag is {@code null} on
   * both sides. Used by the create flow to merge new stock into an existing row instead of
   * inserting a duplicate.
   */
  @Query(
      "SELECT i FROM InventoryItem i WHERE "
          + "i.user = :user AND "
          + "i.material = :material AND "
          + "i.location = :location AND "
          + "i.quality = :quality AND "
          + "((i.mission IS NULL AND :mission IS NULL) OR (i.mission = :mission)) AND "
          + "((i.jobOrder IS NULL AND :jobOrder IS NULL) OR (i.jobOrder = :jobOrder)) AND "
          + "((i.personal IS NULL AND :personal IS NULL) OR (i.personal = :personal))")
  java.util.List<InventoryItem> findMatchingInventoryItem(
      @Param("user") User user,
      @Param("material") Material material,
      @Param("location") Location location,
      @Param("quality") Integer quality,
      @Param("mission") Mission mission,
      @Param("jobOrder") JobOrder jobOrder,
      @Param("personal") Boolean personal);

  /**
   * Pessimistic-write variant of {@link #findMatchingInventoryItem} for the inventory merge
   * race-condition guard. Same seven-dimension match, but acquires a row-level {@code SELECT … FOR
   * UPDATE} on every matched row for the duration of the surrounding transaction.
   *
   * <p>Why: the merge path on inventory create/update and on refinery-order store reads the
   * existing row, adds the incoming amount to its {@code amount}, and writes the sum back. Two
   * callers hitting the same natural-key match concurrently would both read {@code amount = X},
   * both compute {@code X + delta_n}, and the last writer would clobber the other's increment with
   * its own — silent stock loss. Sequentialising the read via {@code PESSIMISTIC_WRITE} makes the
   * second caller block until the first transaction commits, then re-read the post-commit row and
   * compute against the fresh amount. PostgreSQL row locks are released on commit/rollback.
   *
   * <p>Callers MUST be inside a {@code @Transactional} method — Spring Data requires an active
   * transaction to apply the lock, and a no-transaction call would silently drop the lock and fall
   * back to the unlocked read path.
   *
   * @param user the owning user (one of the seven natural-key dimensions)
   * @param material the material reference
   * @param location the location reference
   * @param quality the quality grade
   * @param mission the optional mission reference; {@code null} matches rows where mission is null
   * @param jobOrder the optional job-order reference; {@code null} matches rows where jobOrder is
   *     null
   * @param personal the optional personal flag; {@code null} matches rows where personal is null
   * @return the matching rows (typically zero or one) with row locks held
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT i FROM InventoryItem i WHERE "
          + "i.user = :user AND "
          + "i.material = :material AND "
          + "i.location = :location AND "
          + "i.quality = :quality AND "
          + "((i.mission IS NULL AND :mission IS NULL) OR (i.mission = :mission)) AND "
          + "((i.jobOrder IS NULL AND :jobOrder IS NULL) OR (i.jobOrder = :jobOrder)) AND "
          + "((i.personal IS NULL AND :personal IS NULL) OR (i.personal = :personal))")
  java.util.List<InventoryItem> findMatchingInventoryItemForUpdate(
      @Param("user") User user,
      @Param("material") Material material,
      @Param("location") Location location,
      @Param("quality") Integer quality,
      @Param("mission") Mission mission,
      @Param("jobOrder") JobOrder jobOrder,
      @Param("personal") Boolean personal);

  /**
   * Bulk-reassigns every inventory item owned by {@code oldUser} to {@code newUser}; used by the
   * user-merge flow so stock is preserved when two Keycloak accounts get consolidated.
   */
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE InventoryItem i SET i.user = :newUser WHERE i.user = :oldUser")
  void updateOwner(
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser,
      @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);

  /**
   * Derived Spring-Data query - returns entities matching {@code IdForUpdate}. Acquires a
   * pessimistic write lock for the duration of the surrounding transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"material", "jobOrder", "user", "location", "owningOrgUnit"})
  @Query("SELECT i FROM InventoryItem i WHERE i.id = :id")
  Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);

  /**
   * Bulk-deletes every non-personal inventory item (the "globales Lager" stock). Personal rows
   * ({@code personal = true}) are explicitly left untouched so the admin "clear global inventory"
   * action does not nuke individual users' private entries. The {@code job_order_handover_item ->
   * inventory_item} FK was removed in {@code V64} (the handover row already snapshots the relevant
   * material data), so a single bulk-delete is safe — no pre-cleanup loop is required.
   *
   * <p>Multi-tenant: uses the standard R6.c scope predicate triple. Admin all-scope wipes every
   * non-personal item; a specific active OrgUnit limits the wipe to that OrgUnit; non-admin
   * callers' membership union scopes the wipe to the caller's OrgUnits. Service-layer enforces the
   * access check before reaching this method.
   *
   * @param isAdminAllScope {@code true} iff the caller is admin without an active OrgUnit selection
   *     — wipes every non-personal item regardless of owner.
   * @param activeOrgUnitId the single OrgUnit to scope the wipe to (admin pinning), or {@code
   *     null}.
   * @param memberOrgUnitIds the union of OrgUnits the caller belongs to (non-admin path); empty for
   *     admins and anonymous.
   * @return number of deleted rows
   */
  @Modifying
  @Query(
      "DELETE FROM InventoryItem i WHERE i.personal = false AND ("
          + "  :isAdminAllScope = true"
          + "  OR (:activeOrgUnitId IS NOT NULL AND i.owningOrgUnit.id = :activeOrgUnitId)"
          + "  OR (:activeOrgUnitId IS NULL AND i.owningOrgUnit.id IN :memberOrgUnitIds)"
          + " )")
  int deleteAllNonPersonal(
      @Param("isAdminAllScope") boolean isAdminAllScope,
      @Param("activeOrgUnitId") UUID activeOrgUnitId,
      @Param("memberOrgUnitIds") java.util.Collection<UUID> memberOrgUnitIds);
}
