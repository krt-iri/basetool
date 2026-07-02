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

import de.greluc.krt.profit.basetool.backend.event.JobOrderCreatedEvent;
import de.greluc.krt.profit.basetool.backend.event.OrgUnitRef;
import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.profit.basetool.backend.model.AuditEventType;
import de.greluc.krt.profit.basetool.backend.model.InventoryItem;
import de.greluc.krt.profit.basetool.backend.model.JobOrder;
import de.greluc.krt.profit.basetool.backend.model.JobOrderAssignee;
import de.greluc.krt.profit.basetool.backend.model.JobOrderItem;
import de.greluc.krt.profit.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.profit.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.profit.basetool.backend.model.JobOrderType;
import de.greluc.krt.profit.basetool.backend.model.Material;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CreateJobOrderItemLineDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CreateJobOrderItemRequestDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.profit.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.profit.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.profit.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.profit.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.profit.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import de.greluc.krt.profit.basetool.backend.support.AuditDetails;
import de.greluc.krt.profit.basetool.backend.support.OptimisticLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the lifecycle of job orders — the central work units of the logistics workflow.
 *
 * <p>A job order names a target material at a target quantity, optionally pinned to a minimum
 * quality, and accumulates inventory items as members of the squadron contribute. The service
 * covers create, update (full and per-field), status transitions (OPEN → IN_PROGRESS → COMPLETED /
 * REJECTED), priority reorder, assignee management, material/inventory unlinking, and the priority
 * cleanup helpers.
 *
 * <p>This is one of the services that has shipped concurrency bugs before — the rules in
 * CLAUDE.md's Concurrency section exist because of real incidents on this codepath. The {@link
 * #completeJobOrderWithinTransaction(de.greluc.krt.profit.basetool.backend.model.JobOrder)} method
 * is the canonical example of the {@code …WithinTransaction} pattern: a {@code @Transactional}
 * outer method calls this {@code @Transactional(propagation = MANDATORY)} inner method on an
 * already-managed entity without {@code save()/flush()}, relying on dirty-checking — this avoids a
 * double {@code @Version} bump that would otherwise surface as a 409 to a clean caller. See the
 * rule reference in {@link JobOrderHandoverService#createHandover}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderService {

  private final JobOrderRepository jobOrderRepository;
  private final MaterialRepository materialRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final UserRepository userRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final JobOrderOrgUnitResolver jobOrderOrgUnitResolver;
  private final AuthHelperService authHelperService;
  private final OwnerScopeService ownerScopeService;
  private final ApplicationEventPublisher eventPublisher;
  private final MaterialClaimService materialClaimService;
  private final AuditService auditService;
  private final JobOrderMapper jobOrderMapper;
  private final de.greluc.krt.profit.basetool.backend.mapper.SquadronMapper squadronMapper;
  private final JobOrderItemService jobOrderItemService;
  private final JobOrderStockProjectionService jobOrderStockProjectionService;
  private final de.greluc.krt.profit.basetool.backend.mapper.InventoryItemMapper
      inventoryItemMapper;

  /**
   * Persists a new job order from the create DTO. The next available priority slot is taken
   * automatically (priority 1 is highest); each material's minimum quality is taken verbatim from
   * the DTO (650 or null = Keine).
   *
   * @param createDto create payload
   * @return the persisted order
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when any referenced
   *     material or user id is unknown
   */
  @Transactional
  public JobOrderDto createJobOrder(CreateJobOrderDto createDto) {
    jobOrderRepository.lockAllJobOrders();
    Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;

    OrgUnit responsible =
        jobOrderOrgUnitResolver.resolveResponsibleOrgUnit(createDto.responsibleOrgUnitId());
    OrgUnit requesting =
        jobOrderOrgUnitResolver.resolveRequestingOrgUnit(createDto.requestingOrgUnitId());

    JobOrder jobOrder =
        JobOrder.builder()
            .handle(createDto.handle())
            .comment(normalizeComment(createDto.comment()))
            .priority(newPriority)
            .responsibleOrgUnit(responsible)
            .requestingOrgUnit(requesting)
            .build();

    for (CreateJobOrderMaterialDto matDto : createDto.materials()) {
      Material material =
          materialRepository
              .findById(matDto.materialId())
              .orElseThrow(
                  () -> new NotFoundException("Material not found: " + matDto.materialId()));

      JobOrderMaterial jobOrderMaterial =
          JobOrderMaterial.builder()
              .material(material)
              .minQuality(matDto.minQuality())
              .amount(matDto.amount())
              .build();

      jobOrder.addMaterial(jobOrderMaterial);
    }

    jobOrder = jobOrderRepository.save(jobOrder);
    jobOrderRepository.flush();
    normalizePriorities();
    publishJobOrderCreated(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_CREATED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("type", "MATERIAL")
            .with("materials", jobOrder.getMaterials().size())
            .with("responsibleOrgUnit", orgUnitRef(responsible))
            .with("requestingOrgUnit", orgUnitRef(requesting))
            .with("priority", jobOrder.getPriority()));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Persists a new {@code ITEM} job order from the create DTO. Org-unit stamping and priority
   * assignment mirror {@link #createJobOrder(CreateJobOrderDto)}; the finished-item lines are built
   * (and their required materials derived + snapshotted from each line's blueprint) by {@link
   * JobOrderItemService}. Sub-assembly provenance is reconstructed from the transient {@code
   * clientLineId} / {@code parentClientLineId} hints after every line exists.
   *
   * @param createDto item-order create payload
   * @return the persisted order as a DTO (with derived per-item materials + aggregation)
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when a referenced
   *     game item or blueprint id is unknown
   * @throws BadRequestException when a chosen blueprint does not produce its line's game item, or
   *     when org-unit stamping cannot be resolved
   */
  @Transactional
  public JobOrderDto createItemJobOrder(CreateJobOrderItemRequestDto createDto) {
    jobOrderRepository.lockAllJobOrders();
    Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;

    OrgUnit responsible =
        jobOrderOrgUnitResolver.resolveResponsibleOrgUnit(createDto.responsibleOrgUnitId());
    OrgUnit requesting =
        jobOrderOrgUnitResolver.resolveRequestingOrgUnit(createDto.requestingOrgUnitId());

    JobOrder jobOrder =
        JobOrder.builder()
            .handle(createDto.handle())
            .comment(normalizeComment(createDto.comment()))
            .priority(newPriority)
            .type(JobOrderType.ITEM)
            .responsibleOrgUnit(responsible)
            .requestingOrgUnit(requesting)
            .build();

    Map<Integer, JobOrderItem> byClientId = new HashMap<>();
    List<JobOrderItem> built = new ArrayList<>();
    for (CreateJobOrderItemLineDto line : createDto.items()) {
      JobOrderItem item = jobOrderItemService.buildItemLine(line);
      jobOrder.addItem(item);
      built.add(item);
      if (line.clientLineId() != null) {
        byClientId.put(line.clientLineId(), item);
      }
    }
    // Resolve sub-assembly provenance once every line exists, ignoring dangling or self references.
    for (int i = 0; i < createDto.items().size(); i++) {
      Integer parentClientId = createDto.items().get(i).parentClientLineId();
      if (parentClientId == null) {
        continue;
      }
      JobOrderItem parent = byClientId.get(parentClientId);
      if (parent != null && parent != built.get(i)) {
        built.get(i).setParentItem(parent);
      }
    }

    jobOrder = jobOrderRepository.save(jobOrder);
    jobOrderRepository.flush();
    normalizePriorities();
    publishJobOrderCreated(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_ITEM_CREATED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("type", "ITEM")
            .with("lines", jobOrder.getItems().size())
            .with("responsibleOrgUnit", orgUnitRef(responsible))
            .with("requestingOrgUnit", orgUnitRef(requesting))
            .with("priority", jobOrder.getPriority()));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Publishes a {@link JobOrderCreatedEvent} for a freshly persisted job order so the notification
   * pipeline can fan out after commit. Reads only managed-entity scalars (ids, kinds, shorthand,
   * display id) and never re-saves the order, so it adds no second {@code @Version} bump. The actor
   * is the current authenticated user (empty for anonymous/guest creates).
   *
   * @param jobOrder the persisted, flushed job order
   */
  private void publishJobOrderCreated(JobOrder jobOrder) {
    OrgUnit responsible = jobOrder.getResponsibleOrgUnit();
    OrgUnit requesting = jobOrder.getRequestingOrgUnit();
    eventPublisher.publishEvent(
        new JobOrderCreatedEvent(
            jobOrder.getId(),
            jobOrder.getDisplayId(),
            jobOrder.getHandle(),
            new OrgUnitRef(responsible.getId(), responsible.getKind()),
            responsible.getShorthand(),
            requesting == null ? null : new OrgUnitRef(requesting.getId(), requesting.getKind()),
            jobOrder.getType() == null ? null : jobOrder.getType().name(),
            authHelperService.currentUserId().orElse(null)));
  }

  /**
   * Paged list with optional status filter. Status is the primary discriminator the UI offers as a
   * filter; without it the call returns every status.
   *
   * <p>Delegates to {@link #getAllJobOrders(List, UUID, Pageable)} with a {@code null} squadron
   * display filter — the visibility scope (Phase 3, #343) is always applied regardless.
   *
   * @param statuses optional status filter; null/empty means "all"
   * @param pageable page request
   * @return paged job orders as DTOs
   */
  public Page<JobOrderDto> getAllJobOrders(List<JobOrderStatus> statuses, Pageable pageable) {
    return getAllJobOrders(statuses, null, pageable);
  }

  /**
   * Paged list with optional status filter and an optional squadron display filter, always
   * constrained to the caller's visibility scope (Phase 3, #343).
   *
   * <p>Job Orders are a <em>conditionally</em> staffel-scoped aggregate: an SK-responsible order is
   * public to every squadron, a squadron-responsible order is private to that squadron + admins
   * (the requester does not grant visibility). The scope is resolved from {@link
   * OwnerScopeService#currentScopePredicate()} and pushed into the repository query so a caller can
   * never page past their visibility — admins without a pin see everything, an admin pinned to a
   * squadron (or any non-admin member) sees that scope's private orders plus all SK orders.
   *
   * <p>Layered on top is the viewer-side profit gate ({@link
   * OwnerScopeService#canViewJobOrders()}): a caller who belongs to no profit-eligible org unit
   * (and is not an admin) is not part of the order workflow and receives an empty page — the
   * SK-public union is suppressed for them too.
   *
   * <p>The {@code squadronId} parameter is a pure UI display preference layered on top of the scope
   * (the orders-index "involving my squadron" toggle, matching responsible OR requesting side); it
   * can only narrow the already-scoped result, never widen it.
   *
   * @param statuses optional status filter; null/empty means "all"
   * @param squadronId optional display filter (matches responsible OR requesting); null means "no
   *     display restriction"
   * @param pageable page request
   * @return paged job orders as DTOs, scoped to the caller's visibility
   */
  public Page<JobOrderDto> getAllJobOrders(
      List<JobOrderStatus> statuses, UUID squadronId, Pageable pageable) {
    // Viewer-side profit gate: only members of a profit-eligible org unit (or admins) may see the
    // order queue at all. A non-profit caller gets an empty page instead of the SK-public union, so
    // the list stays invisible to them — the create flow stays open elsewhere. Mirrors the detail
    // gate folded into OwnerScopeService.canSeeJobOrder.
    if (!ownerScopeService.canViewJobOrders()) {
      return Page.empty(pageable);
    }
    // Pass the full enum set when no status filter is requested so the repository's IN clause is
    // never bound with an empty collection (mirrors searchMissions); the boolean-flag alternative
    // would still have to bind an empty list, which JPQL renders inconsistently across dialects.
    List<JobOrderStatus> effectiveStatuses =
        (statuses == null || statuses.isEmpty()) ? List.of(JobOrderStatus.values()) : statuses;
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    Page<JobOrder> page =
        jobOrderRepository.findScopedJobOrders(
            effectiveStatuses,
            squadronId,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            pageable);

    // The whole-page per-row enrichment (batched stock + SK claims, REQ-DATA-003) lives in the
    // extracted projection service alongside the single-order path, so both behave identically.
    return jobOrderStockProjectionService.mapPageWithStock(page);
  }

  /**
   * Lightweight reference projection used by typeaheads and refinery-order pickers (only id +
   * display-id + summary). Filtered to active (non-completed/-rejected) orders and, like the main
   * list endpoint, to the caller's visibility: a non-profit member (no {@code canViewJobOrders()})
   * gets an empty list, and squadron-private orders of other squadrons are filtered out so the
   * typeahead cannot enumerate a foreign squadron's order handle + materials (audit M-2).
   *
   * @return active job orders the caller may see, as reference DTOs
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.JobOrderReferenceDto>
      findAllActiveReference() {
    // M-2: mirror the list endpoint's controls. Viewer-side profit gate first (a non-profit member
    // sees nothing, not even the SK-public union), then per-row visibility scope on the loaded
    // rows.
    if (!ownerScopeService.canViewJobOrders()) {
      return List.of();
    }
    return jobOrderRepository.findAllActiveWithMaterials().stream()
        .filter(ownerScopeService::canSeeJobOrder)
        .map(
            o ->
                new de.greluc.krt.profit.basetool.backend.model.dto.JobOrderReferenceDto(
                    o.getId(),
                    o.getDisplayId(),
                    o.getHandle(),
                    o.getStatus(),
                    squadronMapper.orgUnitToReferenceDto(o.getRequestingOrgUnit()),
                    o.getMaterials() != null
                        ? o.getMaterials().stream().map(jobOrderMapper::toDto).toList()
                        : List.of(),
                    // Both order kinds: ITEM orders have no job_order_material rows, so the picker
                    // must use the kind-agnostic required-material set to filter correctly (#71
                    // orphan-link fix, REQ-ORDERS-018).
                    List.copyOf(jobOrderItemService.requiredMaterialIds(o))))
        .toList();
  }

  /**
   * Returns the order as a DTO.
   *
   * @param id job order primary key
   * @return the order as a DTO
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   */
  public JobOrderDto getJobOrderById(UUID id) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Returns the inventory items eligible for linking to a job order's given material. Used by the
   * order-detail page's "link inventory" picker. The eligibility check filters by material and
   * minimum quality declared on the order's material row, and excludes items already linked to
   * another order.
   *
   * @param jobOrderId target job order
   * @param materialId target material on that order
   * @return list of inventory items as DTOs
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemDto>
      getInventoryItemsForJobOrderMaterial(UUID jobOrderId, UUID materialId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    Material material =
        materialRepository
            .findById(materialId)
            .orElseThrow(() -> new NotFoundException("Material not found: " + materialId));

    return inventoryItemRepository.findByJobOrderIdAndMaterialId(jobOrderId, materialId).stream()
        .map(inventoryItemMapper::toDto)
        .sorted(
            java.util.Comparator.comparing(
                    (de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemDto item) ->
                        item.user() != null && item.user().effectiveName() != null
                            ? item.user().effectiveName()
                            : "",
                    java.util.Comparator.naturalOrder())
                .thenComparing(
                    item -> item.quality() != null ? item.quality() : 0,
                    java.util.Comparator.reverseOrder())
                .thenComparing(
                    item ->
                        item.location() != null && item.location().name() != null
                            ? item.location().name()
                            : "",
                    java.util.Comparator.naturalOrder())
                .thenComparing(
                    item -> item.amount() != null ? item.amount() : 0.0,
                    java.util.Comparator.reverseOrder()))
        .toList();
  }

  /**
   * Returns the inventory items linked to the order whose material the order does <em>not</em>
   * require — "orphaned" links (REQ-ORDERS-019). Because an order's material view is built only
   * from its requirements, such a link binds stock to the order while staying invisible in every
   * material row; surfacing it lets a logistician spot and undo a mis-assignment (e.g. a material
   * linked from the Lager before the link gate of REQ-ORDERS-018 existed). Each linked item's
   * material is compared against the kind-agnostic required-material set ({@link
   * JobOrderItemService#requiredMaterialIds(JobOrder)}), so it is correct for ITEM orders too.
   *
   * @param jobOrderId the order to inspect.
   * @return the orphaned linked inventory items as DTOs, ordered like the per-material drill-down;
   *     empty when every linked item matches a requirement.
   * @throws NotFoundException when the order does not exist.
   */
  public List<de.greluc.krt.profit.basetool.backend.model.dto.InventoryItemDto>
      getOrphanedLinkedInventory(UUID jobOrderId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    Set<UUID> required = jobOrderItemService.requiredMaterialIds(jobOrder);
    return inventoryItemRepository.findByJobOrderIdOrdered(jobOrderId).stream()
        .filter(
            item -> item.getMaterial() == null || !required.contains(item.getMaterial().getId()))
        .map(inventoryItemMapper::toDto)
        .toList();
  }

  /**
   * Updates the status of a JobOrder. This method performs its own {@code findById()} + {@code
   * save()} + {@code flush()} and therefore MUST only be called from a context where the target
   * {@link JobOrder} entity is NOT already managed/dirty in the current persistence context (i.e.
   * called directly from a Controller, not from within another {@code @Transactional} service
   * method that has already modified the same entity).
   *
   * <p><strong>WARNING:</strong> Calling this method from within a running transaction that has
   * already modified the same {@code JobOrder} (e.g. via cascade after {@code
   * jobOrderHandoverRepository.save()}) will cause a double-save that collides with the
   * already-incremented {@code @Version} field, resulting in an {@link
   * org.springframework.orm.ObjectOptimisticLockingFailureException} (HTTP 409). Use {@link
   * #completeJobOrderWithinTransaction(JobOrder)} instead in such cases.
   *
   * @param id job order primary key
   * @param dto status update DTO (carries the new status + expected version)
   * @return the persisted order
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   * @throws de.greluc.krt.profit.basetool.backend.exception.BadRequestException for illegal
   *     transitions
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public JobOrderDto updateJobOrderStatus(UUID id, UpdateJobOrderStatusDto dto) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    OptimisticLock.checkOptionalClient(jobOrder.getVersion(), dto.version(), JobOrder.class, id);

    JobOrderStatus status = dto.status();
    final JobOrderStatus previousStatus = jobOrder.getStatus();
    boolean isTerminal = (status == JobOrderStatus.COMPLETED || status == JobOrderStatus.REJECTED);
    boolean wasTerminal =
        (jobOrder.getStatus() == JobOrderStatus.COMPLETED
            || jobOrder.getStatus() == JobOrderStatus.REJECTED);

    if (isTerminal && !wasTerminal && jobOrder.getPriority() != null) {
      jobOrder.setPriority(null);
    } else if (!isTerminal && wasTerminal) {
      jobOrderRepository.lockAllJobOrders();
      Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;
      jobOrder.setPriority(newPriority);
    }

    jobOrder.setStatus(status);
    jobOrder = jobOrderRepository.save(jobOrder);
    jobOrderRepository.flush();

    if (isTerminal != wasTerminal) {
      normalizePriorities();
    }

    if (isTerminal && !wasTerminal) {
      inventoryItemRepository.unlinkJobOrder(jobOrder.getId());
    }

    // COMPLETED is funneled to one event type whether reached manually here or auto via a handover
    // (completeJobOrderWithinTransaction). A manual completion via this endpoint does NOT go
    // through
    // that funnel, so it is recorded here; emitting only one event per call avoids a STATUS_CHANGED
    // +
    // COMPLETED duplicate. Gate on the actual transition EDGE (not status alone), mirroring the
    // auto-completion funnel: a no-op PUT status=COMPLETED on an already-completed order is a plain
    // STATUS_CHANGED, not a spurious second JOB_ORDER_COMPLETED.
    if (status == JobOrderStatus.COMPLETED && previousStatus != JobOrderStatus.COMPLETED) {
      auditService.record(
          AuditEventType.JOB_ORDER_COMPLETED,
          jobOrder.getId(),
          orderLabel(jobOrder),
          null,
          AuditDetails.of("from", previousStatus).with("autoCompleted", "false"));
    } else {
      auditService.record(
          AuditEventType.JOB_ORDER_STATUS_CHANGED,
          jobOrder.getId(),
          orderLabel(jobOrder),
          null,
          AuditDetails.of("from", previousStatus).with("to", status));
    }

    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Reorders a job order to a new priority position.
   *
   * <p>Backend uses {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} on the whole priority sequence
   * (see {@code JobOrderRepository.lockAllJobOrders}) to serialize concurrent reorders — without
   * it, two simultaneous drag-and-drops would produce duplicate priorities. Adjacent orders shift
   * up or down to make room for the moved row.
   *
   * @param id job order primary key
   * @param newPriority target slot (1-based)
   * @return the persisted order
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional
  public JobOrderDto updateJobOrderPriority(UUID id, Integer newPriority) {
    JobOrder targetOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    Integer oldPriority = targetOrder.getPriority();
    if (oldPriority == null) {
      throw new BadRequestException("Cannot update priority of a completed or rejected job order");
    }
    if (oldPriority.equals(newPriority)) {
      normalizePriorities();
      return jobOrderStockProjectionService.mapToDtoWithStock(targetOrder);
    }

    List<JobOrder> allOrders = jobOrderRepository.lockAllJobOrders();

    List<JobOrder> activeOrders =
        new java.util.ArrayList<>(
            allOrders.stream()
                .filter(o -> o.getPriority() != null)
                .sorted(
                    java.util.Comparator.comparing(JobOrder::getPriority)
                        .thenComparing(JobOrder::getCreatedAt))
                .toList());

    activeOrders.remove(targetOrder);

    // Clamp `newPriority` BEFORE the subtraction so the arithmetic operates on
    // already-sanitised input. `newPriority` is sourced from a request DTO; doing
    // `newPriority - 1` directly and clamping afterwards (the previous shape) trips
    // CodeQL's `java/tainted-arithmetic` rule — it walks the taint into the `- 1`
    // expression and doesn't recognise the post-hoc `if (newIndex < 0)` clamp as a
    // sanitiser. `Math.max(...)` / `Math.min(...)` ARE recognised as sanitisers, so
    // pre-clamping `newPriority` into `[1, activeOrders.size() + 1]` makes the
    // subsequent `- 1` safe by construction (result is in `[0, activeOrders.size()]`,
    // exactly the contract the call site below expects).
    int clampedPriority = Math.max(1, Math.min(activeOrders.size() + 1, newPriority));
    int newIndex = clampedPriority - 1;

    activeOrders.add(newIndex, targetOrder);

    int currentPrio = 1;
    for (JobOrder o : activeOrders) {
      o.setPriority(currentPrio++);
    }

    auditService.record(
        AuditEventType.JOB_ORDER_PRIORITY_CHANGED,
        targetOrder.getId(),
        orderLabel(targetOrder),
        null,
        AuditDetails.of("fromPriority", oldPriority).with("toPriority", targetOrder.getPriority()));
    return jobOrderStockProjectionService.mapToDtoWithStock(targetOrder);
  }

  /**
   * Toggles whether the item order's blueprint-coverage view counts cosmetic variants of the
   * ordered items toward availability (REQ-ORDERS-021, issue #822). {@code true} keeps family-key
   * matching (a member owning any cosmetic variant of an ordered item is counted); {@code false}
   * switches to exact-name matching, so an order for one specific variant counts only owners of
   * that exact blueprint and excludes the family's other variants. A no-op call (the order already
   * carries the requested mode) returns the order unchanged, without bumping its {@code @Version}
   * or recording an audit event.
   *
   * @param id the order id.
   * @param countWithVariants the requested counting mode.
   * @param version the order's expected optimistic-lock version; a stale value triggers a 409.
   * @return the persisted order DTO (carries the bumped version when the mode actually changed).
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no order matches
   *     the id.
   * @throws de.greluc.krt.profit.basetool.backend.exception.BadRequestException when the order is
   *     not an item order.
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale.
   */
  @Transactional
  public JobOrderDto updateBlueprintVariantCounting(
      UUID id, boolean countWithVariants, Long version) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    if (jobOrder.getType() != JobOrderType.ITEM) {
      throw new BadRequestException("Blueprint variant counting applies only to item orders");
    }

    OptimisticLock.checkOptionalClient(jobOrder.getVersion(), version, JobOrder.class, id);

    if (jobOrder.isCountBlueprintsWithVariants() == countWithVariants) {
      // No change: skip the @Version bump (which would needlessly 409 a concurrent edit) and the
      // audit entry. The caller still gets the current state back.
      return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
    }

    jobOrder.setCountBlueprintsWithVariants(countWithVariants);
    // saveAndFlush so the bumped @Version reaches the response DTO: the order-detail panel re-reads
    // the order @Version on its in-place swap, so a stale pre-flush version would 409 the next
    // write.
    // Mirrors updateJobOrder.
    jobOrder = jobOrderRepository.saveAndFlush(jobOrder);

    auditService.record(
        AuditEventType.JOB_ORDER_BLUEPRINT_COUNTING_CHANGED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("countWithVariants", countWithVariants));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Full update of the order's metadata + materials list. Replaces the materials wholesale —
   * removed materials are orphan-removed, kept materials retain their accumulated inventory links.
   *
   * @param id job order primary key
   * @param updateDto update payload (carries the expected version)
   * @return the persisted order
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public JobOrderDto updateJobOrder(UUID id, CreateJobOrderDto updateDto) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    OptimisticLock.checkOptionalClient(
        jobOrder.getVersion(), updateDto.version(), JobOrder.class, id);

    // The responsible org unit is NOT changed on the regular update path — it is only mutated
    // through
    // the dedicated reassignment endpoint (reassignResponsibleOrgUnit) so its permission rules and
    // (Phase 3) visibility consequences stay in one place. updateDto.responsibleOrgUnitId() is
    // therefore ignored here. The requesting (customer) org unit is freely editable by any
    // Logistician+; a null id on update means "leave it unchanged" (minimal-payload contract).
    if (updateDto.requestingOrgUnitId() != null) {
      jobOrder.setRequestingOrgUnit(
          jobOrderOrgUnitResolver.resolveRequestingOrgUnit(updateDto.requestingOrgUnitId()));
    }
    jobOrder.setHandle(updateDto.handle());
    jobOrder.setComment(normalizeComment(updateDto.comment()));

    List<UUID> newMaterialIds =
        updateDto.materials().stream().map(CreateJobOrderMaterialDto::materialId).toList();

    List<UUID> removedMaterialIds =
        jobOrder.getMaterials().stream()
            .map(mat -> mat.getMaterial().getId())
            .filter(matId -> !newMaterialIds.contains(matId))
            .toList();

    for (UUID removedId : removedMaterialIds) {
      inventoryItemRepository.unlinkJobOrderMaterial(jobOrder.getId(), removedId);
    }

    jobOrder.getMaterials().clear();

    for (CreateJobOrderMaterialDto matDto : updateDto.materials()) {
      Material material =
          materialRepository
              .findById(matDto.materialId())
              .orElseThrow(
                  () -> new NotFoundException("Material not found: " + matDto.materialId()));

      JobOrderMaterial jobOrderMaterial =
          JobOrderMaterial.builder()
              .material(material)
              .minQuality(matDto.minQuality())
              .amount(matDto.amount())
              .build();

      jobOrder.addMaterial(jobOrderMaterial);
    }

    // saveAndFlush so the flushed @Version reaches the response DTO: this is an in-place AJAX edit
    // whose returned version is written back onto the edit-modal's hidden version input (which
    // lives
    // outside the swapped fragments), so a stale pre-flush version would 409 the next consecutive
    // edit. The sibling updateItemJobOrder / updateJobOrderStatus already flush; this path was the
    // one order write missed by the #610 sweep.
    jobOrder = jobOrderRepository.saveAndFlush(jobOrder);

    // Reconciliation (Phase 4 / #344, decision #6): an edit that drops a material bucket withdraws
    // any now-orphaned claims on that bucket. No-op for non-SK orders (which carry no claims).
    int orphanedClaimsWithdrawn =
        materialClaimService.withdrawOrphanedClaimsWithinTransaction(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_UPDATED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("materialsRemoved", removedMaterialIds.size())
            .with("materials", jobOrder.getMaterials().size())
            .with("orphanedClaimsWithdrawn", orphanedClaimsWithdrawn));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Full edit of an {@code ITEM} order's ordered-item lines plus its metadata. Rebuilds the item
   * lines from scratch (mirroring {@link #createItemJobOrder}): each line's required materials are
   * re-derived and re-snapshotted from its chosen blueprint, and sub-assembly provenance is
   * reconstructed from the transient {@code clientLineId}/{@code parentClientLineId} hints. Editing
   * is only permitted while the order has <strong>no item-handover</strong> yet — once any partial
   * delivery has been recorded the lines are frozen, because reconciling delivered quantities
   * against a changed line set is out of scope (decision from the item-edit follow-up).
   *
   * <p>Because the derived buckets can change, the orphaned-claim reconciliation hook runs
   * afterwards: a claim on a material+quality bucket that the new lines no longer require is
   * withdrawn (decision #6). Claims are an independent aggregate, so this never bumps the order's
   * {@code @Version} (see {@code MaterialClaimService}). The responsible org unit is not touched
   * here — it is mutated only through the reassignment endpoint, exactly like {@link
   * #updateJobOrder}.
   *
   * @param id the order id.
   * @param updateDto the new item lines + metadata (carries the expected version).
   * @return the persisted order DTO with re-derived materials + aggregation.
   * @throws NotFoundException when the order, a game item or a blueprint id is unknown.
   * @throws BadRequestException when the order is not an item order, already has item-handovers, or
   *     a chosen blueprint does not produce its line's game item.
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale.
   */
  @Transactional
  public JobOrderDto updateItemJobOrder(UUID id, CreateJobOrderItemRequestDto updateDto) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    if (jobOrder.getType() != JobOrderType.ITEM) {
      throw new BadRequestException(
          "Order " + id + " is not an item order; use the material-order update endpoint.");
    }
    OptimisticLock.checkOptionalClient(
        jobOrder.getVersion(), updateDto.version(), JobOrder.class, id);
    if (jobOrder.getItemHandovers() != null && !jobOrder.getItemHandovers().isEmpty()) {
      throw new BadRequestException(
          "Item order " + id + " already has handovers and can no longer be edited.");
    }

    // The requesting (customer) org unit is freely editable; a null id leaves it unchanged. The
    // responsible org unit stays put (reassignment endpoint owns it).
    if (updateDto.requestingOrgUnitId() != null) {
      jobOrder.setRequestingOrgUnit(
          jobOrderOrgUnitResolver.resolveRequestingOrgUnit(updateDto.requestingOrgUnitId()));
    }
    jobOrder.setHandle(updateDto.handle());
    jobOrder.setComment(normalizeComment(updateDto.comment()));

    // Rebuild the ordered-item lines: orphanRemoval cascades the delete of the old lines and their
    // derived JobOrderItemMaterial rows; buildItemLine re-derives + re-snapshots from the
    // blueprint.
    jobOrder.getItems().clear();
    Map<Integer, JobOrderItem> byClientId = new HashMap<>();
    List<JobOrderItem> built = new ArrayList<>();
    for (CreateJobOrderItemLineDto line : updateDto.items()) {
      JobOrderItem item = jobOrderItemService.buildItemLine(line);
      jobOrder.addItem(item);
      built.add(item);
      if (line.clientLineId() != null) {
        byClientId.put(line.clientLineId(), item);
      }
    }
    for (int i = 0; i < updateDto.items().size(); i++) {
      Integer parentClientId = updateDto.items().get(i).parentClientLineId();
      if (parentClientId == null) {
        continue;
      }
      JobOrderItem parent = byClientId.get(parentClientId);
      if (parent != null && parent != built.get(i)) {
        built.get(i).setParentItem(parent);
      }
    }

    jobOrder = jobOrderRepository.save(jobOrder);
    jobOrderRepository.flush();

    // Reconciliation (Phase 4 / #344, decision #6): withdraw claims whose bucket the re-derived
    // lines no longer require.
    int orphanedClaimsWithdrawn =
        materialClaimService.withdrawOrphanedClaimsWithinTransaction(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_ITEM_UPDATED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("lines", jobOrder.getItems().size())
            .with("orphanedClaimsWithdrawn", orphanedClaimsWithdrawn));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Hard-deletes a job order. Backend rejects the delete when linked inventory items exist (must be
   * unlinked first via {@link #unlinkInventoryItem}).
   *
   * @param id job order primary key
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional
  public void deleteJobOrder(UUID id) {
    jobOrderRepository.lockAllJobOrders();
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    final Integer priority = jobOrder.getPriority();
    // Snapshot the order's identity BEFORE the hard delete so the audit row stays readable
    // afterwards (the audit table keeps a plain UUID, no FK to job_order).
    final UUID deletedId = jobOrder.getId();
    final String deletedLabel = orderLabel(jobOrder);
    inventoryItemRepository.unlinkJobOrder(id);
    jobOrderRepository.delete(jobOrder);
    jobOrderRepository.flush();
    if (priority != null) {
      normalizePriorities();
    }
    auditService.record(
        AuditEventType.JOB_ORDER_DELETED,
        deletedId,
        deletedLabel,
        null,
        AuditDetails.of("priorityWas", priority));
  }

  /**
   * Adds an assignee to a job order. Idempotent: re-adding the same user is a no-op.
   *
   * @param jobOrderId job order primary key
   * @param userId user to add
   * @return the persisted order with refreshed assignee list
   * @throws de.greluc.krt.profit.basetool.backend.exception.NotFoundException when either id is
   *     unknown
   */
  @Transactional
  public JobOrderDto addAssignee(UUID jobOrderId, UUID userId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    boolean alreadyAssigned =
        jobOrder.getAssignees().stream()
            .anyMatch(a -> a.getUser() != null && a.getUser().getId().equals(userId));
    if (alreadyAssigned) {
      return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
    }
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    jobOrder.addAssignee(JobOrderAssignee.builder().user(user).build());
    JobOrder saved = jobOrderRepository.saveAndFlush(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_ASSIGNEE_ADDED,
        saved.getId(),
        orderLabel(saved),
        userId,
        AuditDetails.of("assignee", userId));
    return jobOrderStockProjectionService.mapToDtoWithStock(saved);
  }

  /**
   * Removes a material requirement from the order. Inventory items previously linked to this
   * material on this order are unlinked via {@code @Modifying} bulk update — see the {@code
   * clearAutomatically} note in {@link JobOrderHandoverService#createHandover} for why the
   * bulk-update-in-a-loop antipattern matters here.
   *
   * @param jobOrderId job order primary key
   * @param materialId material to unlink
   */
  @Transactional
  public void unlinkMaterial(UUID jobOrderId, UUID materialId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));

    boolean exists =
        jobOrder.getMaterials().stream().anyMatch(m -> m.getMaterial().getId().equals(materialId));
    if (!exists) {
      throw new NotFoundException("Material not linked to job order: " + materialId);
    }

    // Snapshot the label before the @Modifying(clearAutomatically) bulk unlink detaches the
    // context.
    final String label = orderLabel(jobOrder);
    inventoryItemRepository.unlinkJobOrderMaterial(jobOrderId, materialId);

    jobOrder.getMaterials().removeIf(m -> m.getMaterial().getId().equals(materialId));
    jobOrderRepository.save(jobOrder);
    auditService.record(
        AuditEventType.JOB_ORDER_MATERIAL_UNLINKED,
        jobOrderId,
        label,
        null,
        AuditDetails.of("material", materialId));
  }

  /**
   * Detaches a single inventory item from the order. The item stays in the user's inventory; it
   * just stops counting toward the order's completion.
   *
   * @param jobOrderId job order primary key
   * @param inventoryItemId inventory item to detach
   */
  @Transactional
  public void unlinkInventoryItem(UUID jobOrderId, UUID inventoryItemId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));

    InventoryItem item =
        inventoryItemRepository
            .findById(inventoryItemId)
            .orElseThrow(
                () -> new NotFoundException("InventoryItem not found: " + inventoryItemId));

    if (item.getJobOrder() == null || !item.getJobOrder().getId().equals(jobOrderId)) {
      throw new NotFoundException("InventoryItem not linked to job order: " + inventoryItemId);
    }

    item.setJobOrder(null);
    auditService.record(
        AuditEventType.JOB_ORDER_INVENTORY_UNLINKED,
        jobOrderId,
        orderLabel(jobOrder),
        null,
        AuditDetails.of("inventoryItem", inventoryItemId));
  }

  /**
   * Removes an assignee from a job order.
   *
   * @param jobOrderId job order primary key
   * @param userId user to remove
   * @return the persisted order
   */
  @Transactional
  public JobOrderDto removeAssignee(UUID jobOrderId, UUID userId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    boolean removed =
        jobOrder
            .getAssignees()
            .removeIf(a -> a.getUser() != null && a.getUser().getId().equals(userId));
    JobOrder saved = jobOrderRepository.saveAndFlush(jobOrder);
    if (removed) {
      auditService.record(
          AuditEventType.JOB_ORDER_ASSIGNEE_REMOVED,
          saved.getId(),
          orderLabel(saved),
          userId,
          AuditDetails.of("assignee", userId));
    }
    return jobOrderStockProjectionService.mapToDtoWithStock(saved);
  }

  /**
   * Sets (creates or replaces) the note on a user's assignee entry. The note is the assignee's own
   * free-text context — when they work on the order, which part they take. Optimistic-locked on the
   * assignee edge's own version, so a stale client edit surfaces as HTTP 409 without ever bumping
   * the parent order's version.
   *
   * @param jobOrderId job order primary key
   * @param userId the assignee whose note is changed
   * @param note the new note text (already length-validated at the controller boundary)
   * @param version the assignee edge version the client last saw, or {@code null} to skip the check
   * @return the persisted order with the refreshed assignee list
   * @throws NotFoundException when the order or the assignee entry is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code version} is
   *     stale
   */
  @Transactional
  public JobOrderDto updateAssigneeNote(UUID jobOrderId, UUID userId, String note, Long version) {
    return setAssigneeNote(jobOrderId, userId, note, version);
  }

  /**
   * Clears the note on a user's assignee entry. Same optimistic-locking semantics as {@link
   * #updateAssigneeNote}.
   *
   * @param jobOrderId job order primary key
   * @param userId the assignee whose note is cleared
   * @param version the assignee edge version the client last saw, or {@code null} to skip the check
   * @return the persisted order with the refreshed assignee list
   * @throws NotFoundException when the order or the assignee entry is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when {@code version} is
   *     stale
   */
  @Transactional
  public JobOrderDto deleteAssigneeNote(UUID jobOrderId, UUID userId, Long version) {
    return setAssigneeNote(jobOrderId, userId, null, version);
  }

  /**
   * Shared implementation for the note set/clear endpoints: locates the assignee edge, enforces the
   * supplied version against the edge's own {@code @Version}, mutates the note via dirty-checking
   * and flushes so the returned DTO carries the freshly incremented edge version.
   *
   * @param jobOrderId job order primary key
   * @param userId the assignee whose note is changed
   * @param note the new note value, or {@code null} to clear it
   * @param version the assignee edge version the client last saw, or {@code null} to skip the check
   * @return the persisted order with the refreshed assignee list
   */
  private JobOrderDto setAssigneeNote(UUID jobOrderId, UUID userId, String note, Long version) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    JobOrderAssignee assignee =
        jobOrder.getAssignees().stream()
            .filter(a -> a.getUser() != null && a.getUser().getId().equals(userId))
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Assignee not found on job order " + jobOrderId + ": " + userId));

    OptimisticLock.checkOptionalClient(
        assignee.getVersion(), version, JobOrderAssignee.class, assignee.getId());

    String trimmed = (note == null || note.isBlank()) ? null : note.strip();
    assignee.setNote(trimmed);
    JobOrder saved = jobOrderRepository.saveAndFlush(jobOrder);
    // PII: the note body is user free text — record only its presence/length, never the content.
    if (trimmed != null) {
      auditService.record(
          AuditEventType.JOB_ORDER_ASSIGNEE_NOTE_SET,
          saved.getId(),
          orderLabel(saved),
          userId,
          AuditDetails.of("assignee", userId).with("noteLength", trimmed.length()));
    } else {
      auditService.record(
          AuditEventType.JOB_ORDER_ASSIGNEE_NOTE_CLEARED,
          saved.getId(),
          orderLabel(saved),
          userId,
          AuditDetails.of("assignee", userId));
    }
    return jobOrderStockProjectionService.mapToDtoWithStock(saved);
  }

  /**
   * Marks a JobOrder as COMPLETED within an already-running transaction. This method MUST be called
   * from within an active transaction (e.g. from {@code JobOrderHandoverService}) so that the
   * passed {@code jobOrder} entity is already managed by the current persistence context. Using the
   * managed entity directly avoids the double-save / optimistic-lock conflict that occurs when
   * {@link #updateJobOrderStatus} is called with its own {@code findById} inside the caller's
   * transaction.
   *
   * @param jobOrder the managed {@link JobOrder} entity to complete
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void completeJobOrderWithinTransaction(JobOrder jobOrder) {
    boolean wasTerminal =
        (jobOrder.getStatus() == JobOrderStatus.COMPLETED
            || jobOrder.getStatus() == JobOrderStatus.REJECTED);

    if (!wasTerminal && jobOrder.getPriority() != null) {
      jobOrder.setPriority(null);
    }

    jobOrder.setStatus(JobOrderStatus.COMPLETED);

    if (!wasTerminal) {
      // Flush the current state (including the incremented @Version) to the database
      // BEFORE normalizePriorities() issues a PESSIMISTIC_WRITE lock query that re-reads
      // all JobOrder rows. Without this flush, the lock query would read the old version
      // from the DB while Hibernate already holds a newer in-memory version, causing an
      // ObjectOptimisticLockingFailureException on the subsequent flush at transaction end.
      jobOrderRepository.flush();
      normalizePriorities();
      inventoryItemRepository.unlinkJobOrder(jobOrder.getId());
      // Single funnel for auto-completion (every handover path completes through here): one
      // JOB_ORDER_COMPLETED event, recorded only on the actual OPEN/IN_PROGRESS → COMPLETED edge.
      auditService.record(
          AuditEventType.JOB_ORDER_COMPLETED,
          jobOrder.getId(),
          orderLabel(jobOrder),
          null,
          "autoCompleted=true");
    }
  }

  private void normalizePriorities() {
    List<JobOrder> activeOrders =
        jobOrderRepository.lockAllJobOrders().stream()
            .filter(o -> o.getPriority() != null)
            .sorted(
                java.util.Comparator.comparing(JobOrder::getPriority)
                    .thenComparing(JobOrder::getCreatedAt))
            .toList();

    int currentPriority = 1;
    for (JobOrder order : activeOrders) {
      if (order.getPriority() == null || !order.getPriority().equals(currentPriority)) {
        order.setPriority(currentPriority);
      }
      currentPriority++;
    }
  }

  /**
   * Reassigns the responsible (processing) org unit of an existing order. Permission model:
   *
   * <ul>
   *   <li>Admins may reassign freely to any profit-eligible org unit (squadron or SK), in any
   *       direction (escalation, SK→SK, or SK→squadron de-escalation).
   *   <li>A non-admin Logistician/Officer may only <em>escalate</em> a squadron-responsible order
   *       to an SK, and only when they may edit the order's current responsible squadron ({@link
   *       AuthHelperService#canEditOrgUnit}). They cannot hand an order to another squadron, nor
   *       touch an order already responsible to an SK.
   * </ul>
   *
   * <p>The target must be profit-eligible in every case. Visibility consequences of the move are
   * enforced from Phase 3 (#343) on; this method only changes the field and its permission gate.
   *
   * @param id job order id.
   * @param newResponsibleOrgUnitId the target responsible org unit id.
   * @return the updated order DTO.
   * @throws NotFoundException when the order does not exist.
   * @throws BadRequestException when the target is unknown or not profit-eligible.
   * @throws org.springframework.security.access.AccessDeniedException when the caller may not
   *     perform the requested reassignment.
   */
  @Transactional
  public JobOrderDto reassignResponsibleOrgUnit(UUID id, UUID newResponsibleOrgUnitId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    OrgUnit target =
        orgUnitRepository
            .findById(newResponsibleOrgUnitId)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "responsibleOrgUnitId does not resolve to a known org unit: "
                            + newResponsibleOrgUnitId));
    if (!target.isProfitEligible()) {
      throw new BadRequestException(
          "The selected responsible org unit is not profit-eligible and cannot process orders: "
              + newResponsibleOrgUnitId);
    }

    if (!authHelperService.isAdmin()) {
      OrgUnit current = jobOrder.getResponsibleOrgUnit();
      boolean currentIsSquadron = current != null && current.getKind() == OrgUnitKind.SQUADRON;
      boolean targetIsSpecialCommand = target.getKind() == OrgUnitKind.SPECIAL_COMMAND;
      boolean mayEditCurrent = current != null && authHelperService.canEditOrgUnit(current.getId());
      if (!(currentIsSquadron && targetIsSpecialCommand && mayEditCurrent)) {
        throw new org.springframework.security.access.AccessDeniedException(
            "Only an admin may reassign freely; a squadron logistician/officer may only escalate"
                + " their own squadron's order to a Spezialkommando.");
      }
    }

    OrgUnit previous = jobOrder.getResponsibleOrgUnit();
    jobOrder.setResponsibleOrgUnit(target);
    jobOrder = jobOrderRepository.save(jobOrder);
    // Audit (Phase 7, #347): identifiers + kinds only — no PII. MDC is attached per request.
    log.info(
        "Job order {} responsible org unit reassigned: {} ({}) → {} ({})",
        jobOrder.getId(),
        previous != null ? previous.getId() : null,
        previous != null ? previous.getKind() : null,
        target.getId(),
        target.getKind());

    // Reconciliation (Phase 4 / #344, decision #10): an SK→Squadron de-escalation makes the order
    // private, so its public material claims are withdrawn. SK→SK keeps them (still public);
    // Squadron→SK escalation never had any. Claims are an independent aggregate, so this delete
    // does
    // not touch the order's @Version.
    int claimsWithdrawn = 0;
    if (target.getKind() == OrgUnitKind.SQUADRON) {
      claimsWithdrawn = materialClaimService.withdrawAllForOrderWithinTransaction(jobOrder);
    }
    auditService.record(
        AuditEventType.JOB_ORDER_REASSIGNED,
        jobOrder.getId(),
        orderLabel(jobOrder),
        null,
        AuditDetails.of("fromOrgUnit", orgUnitRef(previous))
            .with("toOrgUnit", orgUnitRef(target))
            .with("claimsWithdrawn", claimsWithdrawn));
    return jobOrderStockProjectionService.mapToDtoWithStock(jobOrder);
  }

  /**
   * Normalises an inbound free-text comment: trims surrounding whitespace and collapses a
   * blank/empty result to {@code null} so "comment present" stays unambiguous downstream. Length is
   * already bounded by {@code @Size} at the controller boundary; this method does not log the
   * value.
   *
   * @param comment raw comment from the create/update DTO, may be {@code null}
   * @return the trimmed comment, or {@code null} when absent/blank
   */
  private static String normalizeComment(String comment) {
    if (comment == null) {
      return null;
    }
    String trimmed = comment.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Composes the audit subject label for a job order — {@code #<displayId> '<handle>'}, the
   * deletion-proof identity snapshot stored on each audit event (REQ-AUDIT-001). The handle is a
   * non-personal order title and is safe to snapshot.
   *
   * @param jobOrder the order
   * @return the {@code #<displayId> '<handle>'} label
   */
  private static String orderLabel(JobOrder jobOrder) {
    return "#" + jobOrder.getDisplayId() + " '" + jobOrder.getHandle() + "'";
  }

  /**
   * Renders an org unit for an audit details payload as {@code <id>(<KIND>)}.
   *
   * @param orgUnit the org unit, or {@code null}
   * @return {@code <id>(<KIND>)} or {@code -} when {@code null}
   */
  private static String orgUnitRef(OrgUnit orgUnit) {
    return orgUnit == null ? "-" : orgUnit.getId() + "(" + orgUnit.getKind() + ")";
  }
}
