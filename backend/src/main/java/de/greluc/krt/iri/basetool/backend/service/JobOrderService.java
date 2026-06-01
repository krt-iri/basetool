package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.JobOrderType;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.AggregatedMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemLineDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderItemRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * #completeJobOrderWithinTransaction(de.greluc.krt.iri.basetool.backend.model.JobOrder)} method is
 * the canonical example of the {@code …WithinTransaction} pattern: a {@code @Transactional} outer
 * method calls this {@code @Transactional(propagation = MANDATORY)} inner method on an
 * already-managed entity without {@code save()/flush()}, relying on dirty-checking — this avoids a
 * double {@code @Version} bump that would otherwise surface as a 409 to a clean caller. See the
 * rule reference in {@link JobOrderHandoverService#createHandover}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderService {

  /** System-setting key holding the UUID of the intake SK that guest order creations route to. */
  private static final String INTAKE_SK_SETTING_KEY = "job_order.intake_special_command_id";

  private final JobOrderRepository jobOrderRepository;
  private final MaterialRepository materialRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final UserRepository userRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final SystemSettingService systemSettingService;
  private final AuthHelperService authHelperService;
  private final OwnerScopeService ownerScopeService;
  private final MaterialClaimService materialClaimService;
  private final JobOrderMapper jobOrderMapper;
  private final JobOrderItemService jobOrderItemService;
  private final de.greluc.krt.iri.basetool.backend.mapper.JobOrderItemHandoverMapper
      jobOrderItemHandoverMapper;
  private final de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper inventoryItemMapper;

  /**
   * Persists a new job order from the create DTO. The next available priority slot is taken
   * automatically (priority 1 is highest); each material's minimum quality is taken verbatim from
   * the DTO (700 or null = Keine).
   *
   * @param createDto create payload
   * @return the persisted order
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced
   *     material or user id is unknown
   */
  @Transactional
  public JobOrderDto createJobOrder(CreateJobOrderDto createDto) {
    jobOrderRepository.lockAllJobOrders();
    Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;

    OrgUnit responsible = resolveResponsibleOrgUnit(createDto.responsibleOrgUnitId());
    OrgUnit requesting = resolveRequestingOrgUnit(createDto.requestingOrgUnitId());

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
    return mapToDtoWithStock(jobOrder);
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when a referenced game
   *     item or blueprint id is unknown
   * @throws BadRequestException when a chosen blueprint does not produce its line's game item, or
   *     when org-unit stamping cannot be resolved
   */
  @Transactional
  public JobOrderDto createItemJobOrder(CreateJobOrderItemRequestDto createDto) {
    jobOrderRepository.lockAllJobOrders();
    Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;

    OrgUnit responsible = resolveResponsibleOrgUnit(createDto.responsibleOrgUnitId());
    OrgUnit requesting = resolveRequestingOrgUnit(createDto.requestingOrgUnitId());

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
    return mapToDtoWithStock(jobOrder);
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
    // Pass the full enum set when no status filter is requested so the repository's IN clause is
    // never bound with an empty collection (mirrors searchMissions); the boolean-flag alternative
    // would still have to bind an empty list, which JPQL renders inconsistently across dialects.
    List<JobOrderStatus> effectiveStatuses =
        (statuses == null || statuses.isEmpty()) ? List.of(JobOrderStatus.values()) : statuses;
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return jobOrderRepository
        .findScopedJobOrders(
            effectiveStatuses,
            squadronId,
            scope.adminAllScope(),
            scope.activeOrgUnitId(),
            scope.memberOrgUnitIds(),
            pageable)
        .map(this::mapToDtoWithStock);
  }

  /**
   * Lightweight reference projection used by typeaheads and refinery-order pickers (only id +
   * display-id + summary). Filtered to active (non-completed/-rejected) orders.
   *
   * @return active job orders as reference DTOs
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto>
      findAllActiveReference() {
    return jobOrderRepository.findAllActiveWithMaterials().stream()
        .map(
            o ->
                new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto(
                    o.getId(),
                    o.getDisplayId(),
                    o.getHandle(),
                    o.getStatus(),
                    o.getMaterials() != null
                        ? o.getMaterials().stream().map(jobOrderMapper::toDto).toList()
                        : List.of()))
        .toList();
  }

  /**
   * Returns the order as a DTO.
   *
   * @param id job order primary key
   * @return the order as a DTO
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  public JobOrderDto getJobOrderById(UUID id) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));
    return mapToDtoWithStock(jobOrder);
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
  public List<de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto>
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
                    (de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto item) ->
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
        .collect(Collectors.toList());
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   * @throws de.greluc.krt.iri.basetool.backend.exception.BadRequestException for illegal
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

    if (dto.version() != null
        && jobOrder.getVersion() != null
        && !jobOrder.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(JobOrder.class, id);
    }

    JobOrderStatus status = dto.status();
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

    return mapToDtoWithStock(jobOrder);
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
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
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
      return mapToDtoWithStock(targetOrder);
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

    return mapToDtoWithStock(targetOrder);
  }

  /**
   * Full update of the order's metadata + materials list. Replaces the materials wholesale —
   * removed materials are orphan-removed, kept materials retain their accumulated inventory links.
   *
   * @param id job order primary key
   * @param updateDto update payload (carries the expected version)
   * @return the persisted order
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public JobOrderDto updateJobOrder(UUID id, CreateJobOrderDto updateDto) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    if (updateDto.version() != null
        && jobOrder.getVersion() != null
        && !jobOrder.getVersion().equals(updateDto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(JobOrder.class, id);
    }

    // The responsible org unit is NOT changed on the regular update path — it is only mutated
    // through
    // the dedicated reassignment endpoint (reassignResponsibleOrgUnit) so its permission rules and
    // (Phase 3) visibility consequences stay in one place. updateDto.responsibleOrgUnitId() is
    // therefore ignored here. The requesting (customer) org unit is freely editable by any
    // Logistician+; a null id on update means "leave it unchanged" (minimal-payload contract).
    if (updateDto.requestingOrgUnitId() != null) {
      jobOrder.setRequestingOrgUnit(resolveRequestingOrgUnit(updateDto.requestingOrgUnitId()));
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

    jobOrder = jobOrderRepository.save(jobOrder);

    // Reconciliation (Phase 4 / #344, decision #6): an edit that drops a material bucket withdraws
    // any now-orphaned claims on that bucket. No-op for non-SK orders (which carry no claims).
    materialClaimService.withdrawOrphanedClaimsWithinTransaction(jobOrder);
    return mapToDtoWithStock(jobOrder);
  }

  /**
   * Hard-deletes a job order. Backend rejects the delete when linked inventory items exist (must be
   * unlinked first via {@link #unlinkInventoryItem}).
   *
   * @param id job order primary key
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional
  public void deleteJobOrder(UUID id) {
    jobOrderRepository.lockAllJobOrders();
    JobOrder jobOrder =
        jobOrderRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

    final Integer priority = jobOrder.getPriority();
    inventoryItemRepository.unlinkJobOrder(id);
    jobOrderRepository.delete(jobOrder);
    jobOrderRepository.flush();
    if (priority != null) {
      normalizePriorities();
    }
  }

  /**
   * Adds an assignee to a job order. Idempotent: re-adding the same user is a no-op.
   *
   * @param jobOrderId job order primary key
   * @param userId user to add
   * @return the persisted order with refreshed assignee list
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when either id is
   *     unknown
   */
  @Transactional
  public JobOrderDto addAssignee(UUID jobOrderId, UUID userId) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    jobOrder.getAssignees().add(user);
    return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
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

    inventoryItemRepository.unlinkJobOrderMaterial(jobOrderId, materialId);

    jobOrder.getMaterials().removeIf(m -> m.getMaterial().getId().equals(materialId));
    jobOrderRepository.save(jobOrder);
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
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    jobOrder.getAssignees().remove(user);
    return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
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

  private JobOrderDto mapToDtoWithStock(JobOrder jobOrder) {
    JobOrderDto baseDto = jobOrderMapper.toDto(jobOrder);

    // Phase 5 (#345): on a public SK order, every material/aggregated bucket carries the
    // per-squadron
    // claims + open-remaining; private (squadron) orders carry none (claims empty, openAmount
    // null),
    // so the detail UI renders no claim columns for them.
    Map<String, de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto> claimByBucket =
        isSpecialCommandResponsible(jobOrder)
            ? materialClaimService.getClaimBucketsForOrder(jobOrder).stream()
                .collect(
                    Collectors.toMap(
                        b -> bucketKey(b.material().id(), b.qualityRequirement().name()), b -> b))
            : Map.of();

    List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto> updatedMaterials =
        baseDto.materials().stream()
            .map(
                matDto -> {
                  Double stock =
                      inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(
                          matDto.material().id(), jobOrder.getId(), matDto.minQuality());
                  log.debug(
                      "Stock for job order #{} (ID: {}), material {}: {} / required: {} (min"
                          + " quality: {})",
                      jobOrder.getDisplayId(),
                      jobOrder.getId(),
                      matDto.material().name(),
                      stock,
                      matDto.amount(),
                      matDto.minQuality());
                  // MATERIAL bucket quality mirrors aggregateMaterials(): a 700-floor is GOOD,
                  // "Keine" (null minQuality) is NONE.
                  String qualityName =
                      matDto.minQuality() != null
                          ? de.greluc.krt.iri.basetool.backend.model.QualityRequirement.GOOD.name()
                          : de.greluc.krt.iri.basetool.backend.model.QualityRequirement.NONE.name();
                  de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto bucket =
                      claimByBucket.get(bucketKey(matDto.material().id(), qualityName));
                  return new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto(
                      matDto.id(),
                      matDto.material(),
                      matDto.minQuality(),
                      matDto.amount(),
                      stock != null ? stock : 0.0,
                      bucket != null ? bucket.claims() : List.of(),
                      bucket != null ? bucket.openRemaining() : null,
                      matDto.version());
                })
            .toList();

    boolean isItem = jobOrder.getType() == JobOrderType.ITEM;
    List<JobOrderItemDto> items = isItem ? jobOrderItemService.toItemDtos(jobOrder) : List.of();
    List<AggregatedMaterialDto> aggregatedMaterials =
        isItem ? enrichAggregatedWithClaims(jobOrder, claimByBucket) : List.of();
    List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemHandoverDto> itemHandovers =
        isItem
            ? jobOrder.getItemHandovers().stream().map(jobOrderItemHandoverMapper::toDto).toList()
            : List.of();

    return new JobOrderDto(
        baseDto.id(),
        baseDto.displayId(),
        baseDto.responsibleOrgUnit(),
        baseDto.requestingOrgUnit(),
        baseDto.handle(),
        baseDto.comment(),
        baseDto.priority(),
        baseDto.status(),
        baseDto.type(),
        updatedMaterials,
        items,
        aggregatedMaterials,
        baseDto.assignees(),
        baseDto.handovers(),
        itemHandovers,
        baseDto.createdAt(),
        baseDto.version());
  }

  /**
   * Rebuilds the item order's aggregated-material rows with their per-bucket claims +
   * open-remaining (Phase 5, #345). The base rows come from {@link
   * JobOrderItemService#aggregateMaterials} with neutral claim fields; this overlays the SK claim
   * view. For a non-SK order {@code claimByBucket} is empty, so every row keeps its empty claims /
   * {@code null} open-amount.
   *
   * @param jobOrder the item order.
   * @param claimByBucket the SK claim view keyed by {@link #bucketKey}, or empty for non-SK orders.
   * @return the aggregated rows, claim-enriched.
   */
  private List<AggregatedMaterialDto> enrichAggregatedWithClaims(
      JobOrder jobOrder,
      Map<String, de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto> claimByBucket) {
    return jobOrderItemService.aggregateMaterials(jobOrder).stream()
        .map(
            agg -> {
              de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto bucket =
                  claimByBucket.get(
                      bucketKey(agg.material().id(), agg.qualityRequirement().name()));
              if (bucket == null) {
                return agg;
              }
              return new AggregatedMaterialDto(
                  agg.material(),
                  agg.qualityRequirement(),
                  agg.totalQuantity(),
                  bucket.claims(),
                  bucket.openRemaining());
            })
        .toList();
  }

  /**
   * {@code true} iff the order is responsible to a Spezialkommando — the only orders that carry
   * material claims (Phase 5, #345).
   *
   * @param jobOrder the order.
   * @return whether the order is a public SK order.
   */
  private static boolean isSpecialCommandResponsible(JobOrder jobOrder) {
    return jobOrder.getResponsibleOrgUnit() != null
        && jobOrder.getResponsibleOrgUnit().getKind() == OrgUnitKind.SPECIAL_COMMAND;
  }

  /**
   * Builds the composite key identifying a material bucket ({@code materialId|QUALITY}) used to
   * join claim buckets onto the material / aggregated rows.
   *
   * @param materialId the material id.
   * @param qualityName the {@code GOOD}/{@code NONE} quality name.
   * @return the composite bucket key.
   */
  private static String bucketKey(UUID materialId, String qualityName) {
    return materialId + "|" + qualityName;
  }

  /**
   * Resolves the responsible (processing) org unit for a freshly-created job order.
   *
   * <ul>
   *   <li>Anonymous / guest callers (the public request form is {@code permitAll}) are routed onto
   *       the configured intake Spezialkommando ({@link #INTAKE_SK_SETTING_KEY}); any
   *       client-supplied {@code responsibleOrgUnitId} is ignored so a guest cannot assign work to
   *       an arbitrary unit.
   *   <li>Authenticated callers must supply a {@code responsibleOrgUnitId} that resolves to a
   *       profit-eligible squadron or Spezialkommando — only Profit-side units process orders.
   * </ul>
   *
   * @param responsibleOrgUnitId picker output from the create DTO; required for authenticated
   *     callers, ignored for guests.
   * @return the resolved, profit-eligible responsible org unit; never {@code null}.
   * @throws BadRequestException when the id is missing/unresolvable, the unit is not
   *     profit-eligible, or no intake SK is configured for a guest creation.
   */
  @org.jetbrains.annotations.NotNull
  private OrgUnit resolveResponsibleOrgUnit(
      @org.jetbrains.annotations.Nullable UUID responsibleOrgUnitId) {
    if (!authHelperService.isAuthenticated()) {
      return resolveIntakeSpecialCommand();
    }
    if (responsibleOrgUnitId == null) {
      throw new BadRequestException("responsibleOrgUnitId is required.");
    }
    OrgUnit orgUnit =
        orgUnitRepository
            .findById(responsibleOrgUnitId)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "responsibleOrgUnitId does not resolve to a known org unit: "
                            + responsibleOrgUnitId));
    if (!orgUnit.isProfitEligible()) {
      throw new BadRequestException(
          "The selected responsible org unit is not profit-eligible and cannot process orders: "
              + responsibleOrgUnitId);
    }
    return orgUnit;
  }

  /**
   * Resolves the configured intake Spezialkommando that anonymous/guest order creations are routed
   * to (system setting {@link #INTAKE_SK_SETTING_KEY}). The setting is seeded empty by V128; until
   * an admin selects an SK, guest creation is refused with a 400.
   *
   * @return the configured intake org unit; never {@code null}.
   * @throws BadRequestException when the setting is unset/blank/malformed or no longer resolves.
   */
  @org.jetbrains.annotations.NotNull
  private OrgUnit resolveIntakeSpecialCommand() {
    String raw =
        systemSettingService
            .getSettingValue(INTAKE_SK_SETTING_KEY)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "No intake Spezialkommando is configured; an admin must set it in system"
                            + " settings before guests can create orders."));
    UUID intakeId;
    try {
      intakeId = UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      throw new BadRequestException("Configured intake Spezialkommando id is malformed.");
    }
    return orgUnitRepository
        .findById(intakeId)
        .orElseThrow(
            () -> new BadRequestException("Configured intake Spezialkommando no longer exists."));
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

    jobOrder.setResponsibleOrgUnit(target);
    jobOrder = jobOrderRepository.save(jobOrder);

    // Reconciliation (Phase 4 / #344, decision #10): an SK→Squadron de-escalation makes the order
    // private, so its public material claims are withdrawn. SK→SK keeps them (still public);
    // Squadron→SK escalation never had any. Claims are an independent aggregate, so this delete
    // does
    // not touch the order's @Version.
    if (target.getKind() == OrgUnitKind.SQUADRON) {
      materialClaimService.withdrawAllForOrderWithinTransaction(jobOrder);
    }
    return mapToDtoWithStock(jobOrder);
  }

  /**
   * Resolves the requesting (customer) org unit from the picker output. Any squadron or
   * Spezialkommando is accepted — there is no profit-eligibility restriction on who may place an
   * order. Mandatory: the create/update DTOs always carry it.
   *
   * @param requestingOrgUnitId picker output from the DTO.
   * @return the resolved requesting org unit; never {@code null}.
   * @throws BadRequestException when the id is missing or does not resolve to a known org unit.
   */
  @org.jetbrains.annotations.NotNull
  private OrgUnit resolveRequestingOrgUnit(
      @org.jetbrains.annotations.Nullable UUID requestingOrgUnitId) {
    if (requestingOrgUnitId == null) {
      throw new BadRequestException("requestingOrgUnitId is required.");
    }
    return orgUnitRepository
        .findById(requestingOrgUnitId)
        .orElseThrow(
            () ->
                new BadRequestException(
                    "requestingOrgUnitId does not resolve to a known org unit: "
                        + requestingOrgUnitId));
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
}
