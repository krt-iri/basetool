/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.profit.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.CheckoutType;
import de.greluc.krt.profit.basetool.backend.model.FinanceType;
import de.greluc.krt.profit.basetool.backend.model.InventoryItem;
import de.greluc.krt.profit.basetool.backend.model.JobOrder;
import de.greluc.krt.profit.basetool.backend.model.Location;
import de.greluc.krt.profit.basetool.backend.model.Material;
import de.greluc.krt.profit.basetool.backend.model.Mission;
import de.greluc.krt.profit.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.profit.basetool.backend.model.MissionParticipant;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.profit.basetool.backend.model.dto.BulkCheckoutRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemCreateDto;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemNoteUpdateRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemUpdateDto;
import de.greluc.krt.profit.basetool.backend.model.dto.InventoryStackDto;
import de.greluc.krt.profit.basetool.backend.model.dto.MaterialCollectionEntryDto;
import de.greluc.krt.profit.basetool.backend.model.dto.UpdateDeliveredRequest;
import de.greluc.krt.profit.basetool.backend.model.projection.InventoryStackAggregate;
import de.greluc.krt.profit.basetool.backend.model.projection.OwnedStockSlice;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.LocationRepository;
import de.greluc.krt.profit.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.profit.basetool.backend.repository.MissionRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages inventory items — the squadron's physical stock of refined and raw materials.
 *
 * <p>Each item links to a material (UEX commodity), optionally to a user (the owner, or null for
 * shared/squadron stock), and optionally to a job order or mission. The service covers the read API
 * (aggregated per material, per user, per mission, per job-order, plus the {@code /grouped}
 * variants used by the inventory page), the create/update/delete cycle, the book-out flow (consume
 * / transfer / sell), the bulk-checkout endpoint, and the material-collection roll-up used by the
 * job-order detail page.
 *
 * <p>Concurrency-relevant: the {@link #bookOutInventoryItem} flow touches multiple {@code
 * JobOrderMaterial} rows of the same aggregate. It follows the bulk-update-after-loop pattern
 * (CLAUDE.md): the loop only mutates managed entities (Hibernate dirty-checking), material ids that
 * need a {@code @Modifying(clearAutomatically=true)} bulk update are collected in a {@code
 * Set<UUID>} and the bulk update runs ONCE after the loop. Doing the bulk update inside the loop
 * would detach the entire persistence context and cause spurious 409s on the sibling rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryItemService {

  /**
   * Tolerance used when comparing inventory amounts that are stored as {@code double}. Mirrors
   * {@code JobOrderHandoverService.QUANTITY_EPSILON} — both services compare the same quantity
   * column on {@link de.greluc.krt.profit.basetool.backend.model.InventoryItem} so they need the
   * same rounding-safe threshold. Anything below 1e-4 is floating-point noise (quantities are
   * user-edited at three decimals max), not a real residual.
   */
  private static final double QUANTITY_EPSILON = 1e-4;

  private final InventoryItemRepository inventoryItemRepository;
  private final UserRepository userRepository;
  private final MaterialRepository materialRepository;
  private final LocationRepository locationRepository;
  private final JobOrderRepository jobOrderRepository;
  private final MissionRepository missionRepository;
  private final MissionFinanceEntryRepository missionFinanceEntryRepository;
  private final MissionParticipantRepository missionParticipantRepository;
  private final InventoryItemMapper inventoryItemMapper;
  private final MaterialMapper materialMapper;
  private final OwnerScopeService ownerScopeService;
  private final JobOrderItemService jobOrderItemService;
  private final AuditService auditService;

  /**
   * Pools the caller's entire "My Inventory" stock into one SCU total per (material, quality) pair
   * for the blueprint craftability calculation (#781). Strictly owner-scoped to {@code userId}
   * (both personal and shared rows the user owns count, matching the default {@code /inventory/my}
   * view); never org-unit-scoped, because craftability answers "what can I craft from my stock".
   * The quality is preserved in the result so the calculator can consume the best-quality slices
   * first.
   *
   * @param userId the owning user; never {@code null}
   * @return one slice per (material, quality) the user owns, with the summed SCU; never {@code
   *     null}
   */
  public List<OwnedStockSlice> getOwnedStockSlices(@org.jetbrains.annotations.NotNull UUID userId) {
    return inventoryItemRepository.sumOwnedStockByMaterialAndQuality(userId);
  }

  /**
   * Aggregated per-material inventory view — used by the squadron-wide inventory page.
   *
   * @param pageable page request
   * @return paged aggregated DTOs (material + total amount + average quality)
   */
  public Page<AggregatedInventoryDto> getAggregatedInventory(Pageable pageable) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return inventoryItemRepository
        .getAggregatedInventory(
            scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds(), pageable)
        .map(
            obj ->
                new AggregatedInventoryDto(
                    materialMapper.toDto((Material) obj[0]),
                    obj[1] != null
                        ? Math.round(((Number) obj[1]).doubleValue() * 100.0) / 100.0
                        : 0.0,
                    obj[2] != null ? ((Number) obj[2]).doubleValue() : 0.0));
  }

  /**
   * Per-material drilldown — lists every individual inventory row for the given material. Used by
   * the inventory drilldown page.
   *
   * @param materialId material to drill into
   * @param pageable page request
   * @return paged inventory items (excludes personal items)
   * @throws NotFoundException when the material id is unknown
   */
  public Page<InventoryItemDto> getInventoryByMaterial(UUID materialId, Pageable pageable) {
    Material material =
        materialRepository
            .findById(materialId)
            .orElseThrow(() -> new NotFoundException("Material not found"));
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return inventoryItemRepository
        .findByMaterialAndPersonalFalseScoped(
            material,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * User-scoped inventory list. Excludes personal items because those have their own dedicated
   * service.
   *
   * @param userId owner id
   * @param pageable page request
   * @return paged inventory items owned by the user
   */
  public Page<InventoryItemDto> getUserInventory(UUID userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    return inventoryItemRepository.findByUser(user, pageable).map(inventoryItemMapper::toDto);
  }

  /**
   * Unfiltered convenience overload for {@link #getMyAggregatedInventory(UUID, List, Integer, List,
   * List)}.
   *
   * @param userId owner id
   * @return aggregated items grouped by material
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(UUID userId) {
    return getMyAggregatedInventory(userId, null, null, null, null);
  }

  /**
   * Job-order/mission-filtered convenience overload.
   *
   * @param userId owner id
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(UUID userId, List<UUID> jobOrderIds, List<UUID> missionIds) {
    return getMyAggregatedInventory(userId, null, null, jobOrderIds, missionIds);
  }

  /**
   * Filter-only convenience overload of {@link #getMyAggregatedInventory(UUID, List, Integer, List,
   * List, boolean)} that returns both the caller's shared and personal stacks (no personal-only
   * narrowing).
   *
   * @param userId owner id
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items
   * @throws NotFoundException when the user id is unknown
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(
          UUID userId,
          List<UUID> materialIds,
          Integer minQuality,
          List<UUID> jobOrderIds,
          List<UUID> missionIds) {
    return getMyAggregatedInventory(
        userId, materialIds, minQuality, jobOrderIds, missionIds, false);
  }

  /**
   * Full-filter user-scoped aggregation. Loads the user's items via the parameterized repository
   * query and groups them in memory — the {@code GroupedInventoryDto} shape is what the {@code
   * /grouped} frontend endpoint returns directly.
   *
   * @param userId owner id
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @param personalOnly when {@code true}, narrows the result to the caller's private stock ({@code
   *     personal = true} rows) — the "Mein Lager" personal-entries-only filter; when {@code false},
   *     both shared and personal stacks are returned
   * @return aggregated items
   * @throws NotFoundException when the user id is unknown
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(
          UUID userId,
          List<UUID> materialIds,
          Integer minQuality,
          List<UUID> jobOrderIds,
          List<UUID> missionIds,
          boolean personalOnly) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    List<InventoryStackAggregate> stacks =
        inventoryItemRepository.findUserStacks(
            user.getId(),
            hasMaterials,
            hasMaterials ? materialIds : null,
            minQuality,
            hasJobOrders,
            hasJobOrders ? jobOrderIds : null,
            hasMissions,
            hasMissions ? missionIds : null,
            personalOnly);

    return buildGroupedFromStacks(stacks);
  }

  /**
   * Convenience overload of {@link #getAllAggregatedInventory(List, Integer, List, List)} without
   * job-order/mission filters.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @return aggregated squadron-wide items
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getAllAggregatedInventory(List<UUID> materialIds, Integer minQuality) {
    return getAllAggregatedInventory(materialIds, minQuality, null, null);
  }

  /**
   * Squadron-wide aggregated inventory with the full filter surface. Mirrors {@link
   * #getMyAggregatedInventory} but scopes to all users (admin/logistician view).
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items grouped by material
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      getAllAggregatedInventory(
          List<UUID> materialIds,
          Integer minQuality,
          List<UUID> jobOrderIds,
          List<UUID> missionIds) {
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    List<InventoryStackAggregate> stacks =
        inventoryItemRepository.findGlobalStacks(
            hasMaterials,
            hasMaterials ? materialIds : null,
            minQuality,
            hasJobOrders,
            hasJobOrders ? jobOrderIds : null,
            hasMissions,
            hasMissions ? missionIds : null,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds());

    return buildGroupedFromStacks(stacks);
  }

  /**
   * Assembles the Material → Stack shape the {@code /grouped} views render from the SQL-computed
   * per-stack aggregates. Outer grouping is by material; the individual entries are no longer
   * materialised here — append-only rows grow unboundedly per stack, so a stack's entries are
   * loaded lazily and paginated on expand (ADR-0003, REQ-INV-002, see {@link #getMyStackEntries} /
   * {@link #getAllStackEntries}). Each {@link InventoryStackAggregate} row is one display stack.
   *
   * @param aggregates the SQL-grouped per-stack rows for the current scope/filter
   * @return the materials, each carrying its sorted stacks and material-wide totals
   */
  private List<de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto>
      buildGroupedFromStacks(List<InventoryStackAggregate> aggregates) {
    return aggregates.stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                aggregate -> aggregate.material().getId(),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()))
        .values()
        .stream()
        .map(this::buildMaterialGroup)
        .sorted(java.util.Comparator.comparing(g -> g.material().name()))
        .toList();
  }

  /**
   * Builds one material roll-up from its per-stack aggregates: the stacks (sorted quality desc,
   * location asc, amount desc) plus the material-wide totals (summed amount, amount-weighted mean
   * quality, max quality) accumulated from the raw {@code SUM(amount)} / {@code SUM(amount *
   * quality)} the database returned, so the material average stays independent of per-stack
   * rounding — identical to the previous over-the-entries computation.
   *
   * @param matStacks every per-stack aggregate of one material in the current scope; never empty
   * @return the populated material group with its nested stacks
   */
  private de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto buildMaterialGroup(
      List<InventoryStackAggregate> matStacks) {
    List<InventoryStackDto> stacks = new java.util.ArrayList<>(matStacks.size());
    de.greluc.krt.profit.basetool.backend.model.dto.MaterialReferenceDto material = null;
    double totalAmount = 0.0;
    double weightedQualitySum = 0.0;
    int maxQuality = 0;
    for (InventoryStackAggregate aggregate : matStacks) {
      InventoryItemDto refs = mapAggregateRefs(aggregate);
      if (material == null) {
        material = refs.material();
      }
      double amt = aggregate.totalAmount() != null ? aggregate.totalAmount() : 0.0;
      double wqs = aggregate.weightedQualitySum() != null ? aggregate.weightedQualitySum() : 0.0;
      int mq = aggregate.maxQuality() != null ? aggregate.maxQuality() : 0;
      double stackAvg = amt > 0 ? Math.round((wqs / amt) * 100.0) / 100.0 : 0.0;
      stacks.add(
          new InventoryStackDto(
              refs.user(),
              refs.location(),
              refs.quality(),
              refs.jobOrderId(),
              refs.jobOrderDisplayId(),
              refs.missionId(),
              refs.missionName(),
              refs.personal(),
              refs.owningSquadron(),
              amt,
              stackAvg,
              mq,
              aggregate.entryCount() != null ? aggregate.entryCount().intValue() : 0));
      totalAmount += amt;
      weightedQualitySum += wqs;
      if (mq > maxQuality) {
        maxQuality = mq;
      }
    }
    stacks.sort(STACK_ORDER);
    double avgQuality =
        totalAmount > 0 ? Math.round((weightedQualitySum / totalAmount) * 100.0) / 100.0 : 0.0;
    return new de.greluc.krt.profit.basetool.backend.model.dto.GroupedInventoryDto(
        material, totalAmount, avgQuality, maxQuality, stacks);
  }

  /**
   * Projects one stack aggregate's shared identity entities through the inventory-item mapper to
   * obtain the redaction-safe reference DTOs (user, material, location, owning squadron) and the
   * flattened job-order / mission ids the stack DTO carries. A transient probe {@link
   * InventoryItem} is fed to the mapper so PII redaction and the {@code owningOrgUnit ->
   * owningSquadron} projection behave exactly as they do for a real entry — the probe is never
   * persisted and only its identity fields are read.
   *
   * @param aggregate the per-stack aggregate whose shared identity to project
   * @return an inventory-item DTO carrying only the mapped reference fields (amount/version/id
   *     null)
   */
  private InventoryItemDto mapAggregateRefs(InventoryStackAggregate aggregate) {
    InventoryItem probe = new InventoryItem();
    probe.setUser(aggregate.user());
    probe.setMaterial(aggregate.material());
    probe.setLocation(aggregate.location());
    probe.setQuality(aggregate.quality());
    probe.setJobOrder(aggregate.jobOrder());
    probe.setMission(aggregate.mission());
    probe.setPersonal(aggregate.personal());
    probe.setOwningOrgUnit(aggregate.owningOrgUnit());
    return inventoryItemMapper.toDto(probe);
  }

  /**
   * Lazily loads one of the caller's own stacks' entries, oldest-first, paginated — the per-stack
   * drill-down for the "my inventory" view. Scoped to the caller ({@code userId}); the {@code
   * personal} flag is part of the stock identity, so a private and a shared stack at the same
   * location/quality drill down separately. {@code null} job-order / mission / owning-org-unit
   * arguments match rows where that association is itself {@code null}.
   *
   * @param userId the calling owner whose stack to drill into
   * @param materialId the stack's material
   * @param locationId the stack's storage location
   * @param quality the stack's quality grade, or {@code null}
   * @param jobOrderId the stack's job-order id, or {@code null}
   * @param missionId the stack's mission id, or {@code null}
   * @param personal whether the stack is private stock (defaults to {@code false} when {@code
   *     null})
   * @param owningOrgUnitId the stack's owning org-unit pool id, or {@code null}
   * @param pageable the page request (the query forces oldest-first by creation instant)
   * @return one page of the stack's entries, oldest-first
   */
  public Page<InventoryItemDto> getMyStackEntries(
      UUID userId,
      UUID materialId,
      UUID locationId,
      Integer quality,
      UUID jobOrderId,
      UUID missionId,
      Boolean personal,
      UUID owningOrgUnitId,
      Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    return inventoryItemRepository
        .findUserStackEntries(
            user.getId(),
            materialId,
            locationId,
            quality,
            jobOrderId,
            missionId,
            personal != null ? personal : Boolean.FALSE,
            owningOrgUnitId,
            pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * Lazily loads one global stack's entries, oldest-first, paginated — the per-stack drill-down for
   * the squadron-wide Lager view. The same scope predicate as the grouped view is applied so the
   * drill-down can never widen visibility beyond the caller's org-unit slice; the stack's owner is
   * an explicit argument because a global stack is per-owner. {@code null} job-order / mission /
   * owning-org-unit arguments match rows where that association is itself {@code null}.
   *
   * @param materialId the stack's material
   * @param userId the stack's owning user
   * @param locationId the stack's storage location
   * @param quality the stack's quality grade, or {@code null}
   * @param jobOrderId the stack's job-order id, or {@code null}
   * @param missionId the stack's mission id, or {@code null}
   * @param owningOrgUnitId the stack's owning org-unit pool id, or {@code null}
   * @param pageable the page request (the query forces oldest-first by creation instant)
   * @return one page of the stack's entries, oldest-first
   */
  public Page<InventoryItemDto> getAllStackEntries(
      UUID materialId,
      UUID userId,
      UUID locationId,
      Integer quality,
      UUID jobOrderId,
      UUID missionId,
      UUID owningOrgUnitId,
      Pageable pageable) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return inventoryItemRepository
        .findGlobalStackEntries(
            materialId,
            userId,
            locationId,
            quality,
            jobOrderId,
            missionId,
            owningOrgUnitId,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * Convenience overload without job-order/mission filters.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param pageable page request
   * @return paged inventory items
   */
  public Page<InventoryItemDto> getAllInventory(
      List<UUID> materialIds, Integer minQuality, Pageable pageable) {
    return getAllInventory(materialIds, minQuality, null, null, pageable);
  }

  /**
   * Flat paged squadron-wide inventory with optional filters. Not aggregated — one row per {@code
   * InventoryItem}.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @param pageable page request
   * @return paged inventory items
   */
  public Page<InventoryItemDto> getAllInventory(
      List<UUID> materialIds,
      Integer minQuality,
      List<UUID> jobOrderIds,
      List<UUID> missionIds,
      Pageable pageable) {
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return inventoryItemRepository
        .findGlobalByFilters(
            hasMaterials,
            hasMaterials ? materialIds : null,
            minQuality,
            hasJobOrders,
            hasJobOrders ? jobOrderIds : null,
            hasMissions,
            hasMissions ? missionIds : null,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * Creates a new inventory item. Resolves every shallow id reference (material, location, owner,
   * mission, job order) and rejects with 404 / 400 for unknown ids. Job-order link triggers an
   * eligibility check (material must match the order's material list).
   *
   * @throws NotFoundException when any referenced id is unknown
   * @throws de.greluc.krt.profit.basetool.backend.exception.BadRequestException when the material
   *     does not satisfy the job order's requirements
   */
  @Transactional
  public InventoryItemDto createInventoryItem(
      InventoryItemCreateDto dto, UUID currentUserId, boolean isAdmin) {
    UUID targetUserId = dto.userId() != null ? dto.userId() : currentUserId;
    if (!targetUserId.equals(currentUserId) && !isAdmin) {
      throw new AccessDeniedException(
          "You are not allowed to create inventory items for other users");
    }

    final User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    final Material material =
        materialRepository
            .findById(dto.materialId())
            .orElseThrow(() -> new NotFoundException("Material not found"));
    final Location location =
        locationRepository
            .findById(dto.locationId())
            .orElseThrow(() -> new NotFoundException("Location not found"));

    Mission mission = null;
    if (dto.missionId() != null) {
      mission =
          missionRepository
              .findById(dto.missionId())
              .orElseThrow(() -> new NotFoundException("Mission not found"));
    }

    JobOrder jobOrder = null;
    if (dto.jobOrderId() != null) {
      jobOrder =
          jobOrderRepository
              .findById(dto.jobOrderId())
              .orElseThrow(() -> new NotFoundException("JobOrder not found"));
      assertMaterialRequiredByJobOrder(material, jobOrder);
    }

    Boolean isPersonal = dto.personal() != null ? dto.personal() : false;

    if (Boolean.TRUE.equals(isPersonal) && (mission != null || jobOrder != null)) {
      throw new BadRequestException("Personal items cannot be assigned to a mission or job order");
    }

    // The owning org unit is the eighth dimension of an inventory stack's identity. Resolve it up
    // front (validating the picker output) so the new row is stamped with the correct org-unit
    // pool. Inventory is append-only: every create inserts its own row and is never folded into an
    // existing stack — rows that share the stack identity are grouped only for display
    // (group-on-read, see aggregateInventoryItems). This also removes the read-add-write race the
    // former merge path had to guard with a pessimistic lock.
    final OrgUnit owningOrgUnit =
        ownerScopeService.resolveOrgUnitForPickerOutputNullable(user, dto.owningOrgUnitId());

    InventoryItem item = new InventoryItem();
    item.setUser(user);
    item.setOwningOrgUnit(owningOrgUnit);
    item.setMaterial(material);
    item.setLocation(location);
    item.setQuality(dto.quality());
    item.setAmount(roundAmount(dto.amount()));
    item.setPersonal(isPersonal);
    item.setMission(mission);
    item.setJobOrder(jobOrder);

    InventoryItem saved = inventoryItemRepository.save(item);
    auditService.record(
        AuditEventType.INVENTORY_ITEM_CREATED,
        item.getId(),
        inventoryLabel(item),
        item.getUser().getId(),
        "qty="
            + item.getAmount()
            + " q="
            + item.getQuality()
            + " personal="
            + item.getPersonal()
            + " jobOrder="
            + jobOrderRef(item)
            + " mission="
            + (item.getMission() != null ? item.getMission().getName() : "-"));
    return inventoryItemMapper.toDto(saved);
  }

  /**
   * Updates the soft associations of an inventory item (mission, job order, owner). Quantity and
   * material identity are NOT mutable here — those go through {@link #bookOutInventoryItem} so the
   * audit trail stays consistent.
   *
   * @throws NotFoundException when any referenced id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public InventoryItemDto updateInventoryItem(
      UUID id, InventoryItemUpdateDto dto, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (!item.getUser().getId().equals(currentUserId)) {
      if (item.getPersonal() || !isLogistician) {
        throw new AccessDeniedException("You are not allowed to update this inventory item");
      }
    }

    if (dto.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    Boolean isPersonal = dto.personal() != null ? dto.personal() : item.getPersonal();
    item.setPersonal(isPersonal);

    if (Boolean.TRUE.equals(isPersonal) && (dto.missionId() != null || dto.jobOrderId() != null)) {
      throw new BadRequestException("Personal items cannot be assigned to a mission or job order");
    }

    Material material =
        materialRepository
            .findById(dto.materialId())
            .orElseThrow(() -> new NotFoundException("Material not found"));
    item.setMaterial(material);

    Location location =
        locationRepository
            .findById(dto.locationId())
            .orElseThrow(() -> new NotFoundException("Location not found"));
    item.setLocation(location);

    item.setQuality(dto.quality());
    item.setAmount(roundAmount(dto.amount()));

    if (dto.jobOrderId() != null) {
      JobOrder jobOrder =
          jobOrderRepository
              .findById(dto.jobOrderId())
              .orElseThrow(() -> new NotFoundException("JobOrder not found"));
      assertMaterialRequiredByJobOrder(material, jobOrder);
      item.setJobOrder(jobOrder);
    } else {
      item.setJobOrder(null);
    }

    if (dto.missionId() != null) {
      Mission mission =
          missionRepository
              .findById(dto.missionId())
              .orElseThrow(() -> new NotFoundException("Mission not found"));
      item.setMission(mission);
    } else {
      item.setMission(null);
    }

    // Append-only: an update edits the row in place and is never folded into another matching
    // stack.
    // Rows that now share a stack identity are grouped only for display (group-on-read).
    // saveAndFlush (not save): this method's @Transactional commits AFTER it returns, so a plain
    // save() leaves the @Version increment unflushed and the mapped DTO carries the STALE version.
    // The client writes that back, and the user's NEXT in-place edit of the same row then 409s.
    // Flushing here makes the response @Version authoritative (REQ-FE-003).
    InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
    auditService.record(
        AuditEventType.INVENTORY_ITEM_UPDATED,
        item.getId(),
        inventoryLabel(item),
        item.getUser().getId(),
        "qty="
            + item.getAmount()
            + " q="
            + item.getQuality()
            + " personal="
            + item.getPersonal()
            + " jobOrder="
            + jobOrderRef(item)
            + " mission="
            + (item.getMission() != null ? item.getMission().getName() : "-"));
    return inventoryItemMapper.toDto(saved);
  }

  /**
   * Rejects linking an inventory item to a job order whose requirements do not include the item's
   * material (REQ-ORDERS-018). An order's material view is built solely from its requirements
   * (material lines for a MATERIAL order, blueprint-derived materials for an ITEM order) with
   * linked stock matched onto those rows; a link for a non-required material therefore never
   * surfaces in the order — it would bind stock to the order while staying invisible (an orphaned
   * link). The authoritative required-material set is {@link
   * JobOrderItemService#requiredMaterialIds(JobOrder)}, which covers both order kinds. Both call
   * sites are {@code @Transactional}, so the helper's lazy walk of the order's item/material
   * collections is safe.
   *
   * @param material the inventory item's material.
   * @param jobOrder the order the item is being linked to.
   * @throws BadRequestException when the order does not require the material.
   */
  private void assertMaterialRequiredByJobOrder(Material material, JobOrder jobOrder) {
    if (!jobOrderItemService.requiredMaterialIds(jobOrder).contains(material.getId())) {
      throw new BadRequestException(
          "Material "
              + material.getId()
              + " is not required by job order "
              + jobOrder.getId()
              + "; an inventory item can only be linked to an order that needs its material.");
    }
  }

  /**
   * Sets, updates or removes the free-text note of an inventory item.
   *
   * <p>Access rules:
   *
   * <ul>
   *   <li>The owner of the item may always modify its note.
   *   <li>A non-owner may only modify the note when {@code isLogistician} is {@code true} (i.e.
   *       they hold {@code ROLE_LOGISTICIAN} or a role that inherits it such as {@code
   *       ROLE_OFFICER}/{@code ROLE_ADMIN}).
   * </ul>
   *
   * <p>A blank or empty note is normalized to {@code null} and thus effectively removes the note.
   * Optimistic locking is enforced via the supplied {@code version}.
   *
   * @throws NotFoundException when the item is unknown
   */
  @Transactional
  public InventoryItemDto updateNote(
      UUID id, InventoryItemNoteUpdateRequest request, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    boolean isOwner = item.getUser().getId().equals(currentUserId);
    if (!isOwner && !isLogistician) {
      throw new AccessDeniedException(
          "You are not allowed to modify the note of this inventory item");
    }

    if (request.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    String normalizedNote = request.note();
    if (normalizedNote != null) {
      normalizedNote = normalizedNote.trim();
      if (normalizedNote.isEmpty()) {
        normalizedNote = null;
      }
    }
    item.setNote(normalizedNote);

    // saveAndFlush so the response carries the post-increment @Version (see updateInventoryItem) —
    // otherwise editing a note right after an association change 409s.
    InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
    // PII: the note body is user free text — record only its presence/length, never the content.
    auditService.record(
        AuditEventType.INVENTORY_ITEM_NOTE_UPDATED,
        item.getId(),
        inventoryLabel(item),
        item.getUser().getId(),
        normalizedNote == null
            ? "note=cleared"
            : "note=present(" + normalizedNote.length() + " chars)");
    return inventoryItemMapper.toDto(saved);
  }

  /**
   * Consumes or transfers an inventory item.
   *
   * <p>The {@code type} discriminator selects: CONSUME (just decrement), TRANSFER (decrement here,
   * then insert a new row for the moved quantity at the target location/owner — inventory is
   * append-only, so the moved stock is never folded into an existing target stack), SELL (decrement
   * here, create a finance entry for the participant). When the post-decrement quantity is below
   * {@link #QUANTITY_EPSILON} the row is removed entirely. Fulfills attached job-order materials
   * when the amount delivered reaches the required quantity.
   *
   * <p>Follows the bulk-update-after-loop concurrency pattern: collects every {@code
   * JobOrderMaterial} id that may be ready for a clearing bulk update in a {@code Set<UUID>},
   * applies all mutations through dirty-checking inside the loop, then runs the bulk update exactly
   * once after the loop. Re-fetches the aggregate root for the completion check so {@link
   * JobOrderService#completeJobOrderWithinTransaction} sees the freshly-incremented
   * {@code @Version}.
   *
   * @throws NotFoundException when the item is unknown
   * @throws de.greluc.krt.profit.basetool.backend.exception.BadRequestException when the requested
   *     amount exceeds the available quantity
   */
  @Transactional
  public InventoryItemDto bookOutInventoryItem(
      UUID id, InventoryItemBookOutDto dto, UUID currentUserId, boolean isAdmin) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (dto.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    if (!item.getUser().getId().equals(currentUserId) && !isAdmin) {
      throw new AccessDeniedException("You are not allowed to book out this inventory item");
    }

    if (dto.amount() > item.getAmount()) {
      throw new BadRequestException("Cannot book out more than the available amount");
    }

    CheckoutType checkoutType = dto.type();
    if (checkoutType == null) {
      checkoutType =
          (dto.targetUserId() != null || dto.targetLocationId() != null)
              ? CheckoutType.TRANSFER
              : CheckoutType.DISCARD;
    }

    if (checkoutType == CheckoutType.SELL) {
      if (dto.terminal() == null || dto.terminal().isBlank()) {
        throw new BadRequestException("Terminal is required for selling");
      }
      if (dto.sellAmount() == null || dto.sellAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
        throw new BadRequestException("Sell amount is required and must be positive");
      }
    }

    double remainingAmount = roundAmount(item.getAmount() - dto.amount());

    // Snapshot the source row's scalar identity BEFORE any decrement/delete so the audit row stays
    // accurate even when the source is depleted to zero and removed (bulk-clear landmine rules).
    final UUID sourceId = item.getId();
    final String sourceLabel = inventoryLabel(item);
    final String materialName = item.getMaterial() != null ? item.getMaterial().getName() : "—";
    final UUID sourceOwnerId = item.getUser().getId();
    final boolean depleted = remainingAmount <= QUANTITY_EPSILON;
    UUID financeEntryId = null;

    if (checkoutType == CheckoutType.TRANSFER
        && (dto.targetUserId() != null || dto.targetLocationId() != null)) {
      User targetUser = item.getUser();
      if (dto.targetUserId() != null && !dto.targetUserId().equals(item.getUser().getId())) {
        targetUser =
            userRepository
                .findById(dto.targetUserId())
                .orElseThrow(() -> new NotFoundException("Target user not found"));
      }

      Location targetLocation = item.getLocation();
      if (dto.targetLocationId() != null
          && !dto.targetLocationId().equals(item.getLocation().getId())) {
        targetLocation =
            locationRepository
                .findById(dto.targetLocationId())
                .orElseThrow(() -> new NotFoundException("Target location not found"));
      }

      if (targetUser.getId().equals(item.getUser().getId())
          && targetLocation.getId().equals(item.getLocation().getId())) {
        throw new BadRequestException("Transfer must change either the user or the location");
      }

      // Resolve the target stack's owning org-unit pool up front — the eighth identity dimension —
      // so the freshly created target row is stamped with that pool.
      final OrgUnit targetOwningOrgUnit =
          ownerScopeService.resolveOrgUnitForPickerOutputNullable(
              targetUser, dto.targetOwningOrgUnitId());

      // Append-only: a transfer always inserts its own row at the target and is never folded into
      // an existing identical stack there. The view collapses rows that share a stack identity for
      // display (group-on-read), so no duplicate is visible and no read-add-write race exists.
      InventoryItem newItem = new InventoryItem();
      newItem.setUser(targetUser);
      newItem.setOwningOrgUnit(targetOwningOrgUnit);
      newItem.setMaterial(item.getMaterial());
      newItem.setLocation(targetLocation);
      newItem.setQuality(item.getQuality());
      newItem.setAmount(roundAmount(dto.amount()));
      newItem.setPersonal(item.getPersonal());
      newItem.setJobOrder(item.getJobOrder());
      newItem.setMission(item.getMission());
      InventoryItem savedNew = inventoryItemRepository.save(newItem);
      if (remainingAmount <= QUANTITY_EPSILON) {
        inventoryItemRepository.delete(item);
      } else {
        item.setAmount(remainingAmount);
        // saveAndFlush the reduced source row for parity with the discard/sell fall-through below:
        // the returned DTO is the new target row, but flushing keeps the source row's @Version
        // current within the transaction so any future in-place consumer of a transfer cannot 409.
        inventoryItemRepository.saveAndFlush(item);
      }
      auditService.record(
          AuditEventType.INVENTORY_ITEM_TRANSFERRED,
          sourceId,
          sourceLabel,
          targetUser.getId(),
          "material="
              + materialName
              + " amount="
              + dto.amount()
              + " toLoc="
              + (targetLocation != null ? targetLocation.getName() : "—")
              + " newRow="
              + newItem.getId()
              + " depleted="
              + depleted);
      return inventoryItemMapper.toDto(savedNew);
    } else if (checkoutType == CheckoutType.SELL && item.getMission() != null) {
      MissionParticipant participant =
          missionParticipantRepository
              .findByMissionIdAndUserId(item.getMission().getId(), currentUserId)
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          "You must be a participant of the mission to sell its items"));

      MissionFinanceEntry entry = new MissionFinanceEntry();
      entry.setMission(item.getMission());
      entry.setParticipant(participant);
      entry.setType(FinanceType.INCOME);
      entry.setAmount(dto.sellAmount());
      entry.setNote(
          "Sale of "
              + dto.amount()
              + "x "
              + item.getMaterial().getName()
              + " at "
              + dto.terminal());
      missionFinanceEntryRepository.save(entry);
      // Read the id off the managed entity (set by save()); the dedicated capture avoids relying on
      // the save() return value, which a unit-test mock leaves null.
      financeEntryId = entry.getId();
    }

    if (remainingAmount <= QUANTITY_EPSILON) { // Floating point precision safety
      inventoryItemRepository.delete(item);
      recordBookOutTail(
          checkoutType,
          sourceId,
          sourceLabel,
          materialName,
          sourceOwnerId,
          dto,
          0.0,
          financeEntryId);
      return null;
    } else {
      item.setAmount(remainingAmount);
      // saveAndFlush so a partial book-out's response carries the fresh @Version (see
      // updateInventoryItem) — otherwise a follow-up edit of the reduced row 409s.
      InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
      recordBookOutTail(
          checkoutType,
          sourceId,
          sourceLabel,
          materialName,
          sourceOwnerId,
          dto,
          remainingAmount,
          financeEntryId);
      return inventoryItemMapper.toDto(saved);
    }
  }

  /**
   * Records the audit event for the consume/sell tail of {@link #bookOutInventoryItem} (the
   * transfer branch records its own event). SELL events carry the
   * terminal/sell-amount/finance-entry; DISCARD events carry the consumed/remaining amounts.
   *
   * @param type the resolved checkout type (never {@code TRANSFER} here)
   * @param sourceId the source row id (snapshotted before a possible delete)
   * @param sourceLabel the source row's {@code material @ location} label snapshot
   * @param materialName the material name snapshot
   * @param ownerId the source row owner's id
   * @param dto the book-out request (read for terminal/sell amount)
   * @param remaining the post-decrement amount (0 when the row was depleted)
   * @param financeEntryId the created finance entry id for a SELL with a mission, or {@code null}
   */
  private void recordBookOutTail(
      CheckoutType type,
      UUID sourceId,
      String sourceLabel,
      String materialName,
      UUID ownerId,
      InventoryItemBookOutDto dto,
      double remaining,
      UUID financeEntryId) {
    boolean rowDepleted = remaining <= QUANTITY_EPSILON;
    if (type == CheckoutType.SELL) {
      auditService.record(
          AuditEventType.INVENTORY_ITEM_SOLD,
          sourceId,
          sourceLabel,
          ownerId,
          "material="
              + materialName
              + " amount="
              + dto.amount()
              + " terminal="
              + dto.terminal()
              + " sellAmount="
              + dto.sellAmount()
              + " financeEntry="
              + (financeEntryId != null ? financeEntryId : "-")
              + " depleted="
              + rowDepleted);
    } else {
      auditService.record(
          AuditEventType.INVENTORY_ITEM_CONSUMED,
          sourceId,
          sourceLabel,
          ownerId,
          "type="
              + type
              + " material="
              + materialName
              + " amount="
              + dto.amount()
              + " remaining="
              + remaining
              + " depleted="
              + rowDepleted);
    }
  }

  /**
   * Removes every non-personal inventory item from the database — the admin "globales Lager leeren"
   * action. Personal entries ({@code personal = true}) are kept on purpose: they belong to
   * individual users and live outside the squadron's shared stock.
   *
   * <p>Implemented as a single bulk {@code DELETE} via {@link
   * InventoryItemRepository#deleteAllNonPersonal()}. The previously load-bearing FK {@code
   * job_order_handover_item.inventory_item_id} was dropped in migration {@code V64} (handover rows
   * snapshot the material data directly), so no pre-cleanup of dependent rows is needed and no
   * {@code @Modifying(clearAutomatically = true)} loop is required — the operation is a one-shot
   * bulk statement that does not collide with any sibling-aggregate {@code @Version}.
   *
   * @return number of inventory rows deleted (0 if the global inventory was already empty)
   */
  @Transactional
  public int deleteAllGlobalInventory() {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    log.info(
        "Bulk delete of global inventory requested (adminAll={}, active={}, members={})",
        scope.adminAllScope(),
        scope.activeOrgUnitId(),
        scope.memberOrgUnitIds().size());
    int removed =
        inventoryItemRepository.deleteAllNonPersonal(
            scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds());
    log.info(
        "Bulk delete of global inventory completed: {} item(s) removed (adminAll={}, active={})",
        removed,
        scope.adminAllScope(),
        scope.activeOrgUnitId());
    auditService.record(
        AuditEventType.INVENTORY_WIPED,
        null,
        null,
        null,
        "scope="
            + (scope.adminAllScope() ? "adminAll" : "active=" + scope.activeOrgUnitId())
            + " removed="
            + removed);
    return removed;
  }

  /**
   * Bulk-checkout: removes all inventory items with the given IDs that belong to the authenticated
   * user. Associations to JobOrders and Missions are cleared on the managed entities inside the
   * loop (no @Modifying bulk-update inside the loop). The actual deleteAllById call happens after
   * the loop, in one batch.
   *
   * @param request the bulk checkout request containing item IDs
   * @param currentUserId the UUID of the authenticated user (JWT sub)
   */
  @Transactional
  public void bulkCheckout(BulkCheckoutRequest request, UUID currentUserId) {
    log.info(
        "Bulk checkout requested by user {} for {} items", currentUserId, request.itemIds().size());

    List<UUID> toDelete = new ArrayList<>();

    for (UUID itemId : request.itemIds()) {
      InventoryItem item =
          inventoryItemRepository
              .findByIdForUpdate(itemId)
              .orElseThrow(() -> new NotFoundException("Inventory item not found: " + itemId));

      if (!item.getUser().getId().equals(currentUserId)) {
        log.warn(
            "User {} attempted to bulk-checkout item {} owned by {}",
            currentUserId,
            itemId,
            item.getUser().getId());
        throw new AccessDeniedException(
            "You are not allowed to check out inventory item: " + itemId);
      }

      // Clear associations on the managed entity – no @Modifying query inside the loop
      item.setJobOrder(null);
      item.setMission(null);

      toDelete.add(itemId);
    }

    // Flush association changes, then delete all in one batch
    inventoryItemRepository.flush();
    inventoryItemRepository.deleteAllById(toDelete);
    log.info(
        "Bulk checkout completed: {} items removed for user {}", toDelete.size(), currentUserId);
    auditService.record(
        AuditEventType.INVENTORY_BULK_CHECKED_OUT,
        null,
        null,
        currentUserId,
        "count=" + toDelete.size());
  }

  /**
   * Returns all inventory items linked to the given job order, sorted server-side by owner name,
   * location, material name, quality (desc), quantity (desc).
   *
   * @param jobOrderId the UUID of the job order
   * @return sorted list of {@link MaterialCollectionEntryDto}
   * @throws NotFoundException when the job order is unknown
   */
  public List<MaterialCollectionEntryDto> getMaterialCollection(UUID jobOrderId) {
    jobOrderRepository
        .findById(jobOrderId)
        .orElseThrow(() -> new NotFoundException("Job order not found"));
    return inventoryItemRepository.findByJobOrderIdOrdered(jobOrderId).stream()
        .map(
            item -> {
              String ownerName =
                  item.getUser().getDisplayName() != null
                      ? item.getUser().getDisplayName()
                      : item.getUser().getUsername();
              return new MaterialCollectionEntryDto(
                  item.getId(),
                  item.getVersion() != null ? item.getVersion() : 0L,
                  ownerName,
                  item.getUser().getId(),
                  item.getLocation().getName(),
                  item.getLocation().getId(),
                  item.getMaterial().getName(),
                  item.getQuality() != null ? item.getQuality().doubleValue() : null,
                  item.getAmount(),
                  Boolean.TRUE.equals(item.getDelivered()));
            })
        .toList();
  }

  /**
   * Updates the delivered status of an inventory item. Applies optimistic locking via the version
   * field.
   *
   * @param id the UUID of the inventory item
   * @param request the update request containing delivered flag and version
   * @param currentUserId the UUID of the authenticated user
   * @param isLogistician whether the user has logistician or higher role
   * @return updated {@link InventoryItemDto}
   * @throws NotFoundException when the item is unknown
   */
  @Transactional
  public InventoryItemDto updateDelivered(
      UUID id, UpdateDeliveredRequest request, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (!item.getUser().getId().equals(currentUserId) && !isLogistician) {
      throw new AccessDeniedException("You are not allowed to update this inventory item");
    }

    if (item.getVersion() != null && !item.getVersion().equals(request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    item.setDelivered(request.delivered());
    // saveAndFlush so the response carries the flushed @Version — the material-collection delivered
    // checkbox syncs the returned version onto the row in place (no reload), so a plain save would
    // return the stale pre-flush version and a second consecutive toggle of the same row would 409.
    InventoryItem saved = inventoryItemRepository.saveAndFlush(item);
    auditService.record(
        AuditEventType.INVENTORY_ITEM_DELIVERY_TOGGLED,
        item.getId(),
        inventoryLabel(item),
        item.getUser().getId(),
        "delivered=" + request.delivered() + " jobOrder=" + jobOrderRef(saved));
    return inventoryItemMapper.toDto(saved);
  }

  /**
   * Composes the audit subject label for an inventory row — {@code material @ location}, the
   * deletion-proof identity snapshot stored on each audit event (REQ-AUDIT-001).
   *
   * @param item the inventory row (associations may be lazily loaded but are within the tx)
   * @return the {@code material @ location} label
   */
  private static String inventoryLabel(InventoryItem item) {
    String mat = item.getMaterial() != null ? item.getMaterial().getName() : "—";
    String loc = item.getLocation() != null ? item.getLocation().getName() : "—";
    return mat + " @ " + loc;
  }

  /**
   * Renders an inventory row's job-order reference for an audit details payload.
   *
   * @param item the inventory row
   * @return {@code #<displayId>} when linked, {@code -} otherwise
   */
  private static String jobOrderRef(InventoryItem item) {
    return item.getJobOrder() != null ? "#" + item.getJobOrder().getDisplayId() : "-";
  }

  private Double roundAmount(Double amount) {
    if (amount == null) {
      return null;
    }
    return java.math.BigDecimal.valueOf(amount)
        .setScale(3, java.math.RoundingMode.HALF_UP)
        .doubleValue();
  }

  /**
   * Display order of the stacks within a material: highest quality first, then location name
   * ascending, then largest total amount first — mirrors the previous per-row ordering.
   */
  private static final java.util.Comparator<InventoryStackDto> STACK_ORDER =
      java.util.Comparator.<InventoryStackDto, Integer>comparing(
              s -> s.quality() != null ? s.quality() : 0)
          .reversed()
          .thenComparing(
              s -> s.location() != null && s.location().name() != null ? s.location().name() : "")
          .thenComparing(
              java.util.Comparator.<InventoryStackDto, Double>comparing(
                      s -> s.totalAmount() != null ? s.totalAmount() : 0.0)
                  .reversed());
}
