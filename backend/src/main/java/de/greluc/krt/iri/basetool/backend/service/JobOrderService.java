package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.List;
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

  /**
   * Default minimum quality assigned to every {@link
   * de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial} when a job order is created or
   * updated without an explicit minimum on the wire. 700 is the conventional refining-grade floor
   * used across the squadron's existing orders; it is hard-coded for now so the value lives in code
   * review rather than in a database row that can drift silently. If the squadron starts adjusting
   * this per order, promote it to a {@code SystemSetting} entry.
   */
  private static final int MIN_QUALITY_DEFAULT = 700;

  private final JobOrderRepository jobOrderRepository;
  private final MaterialRepository materialRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final UserRepository userRepository;
  private final SquadronRepository squadronRepository;
  private final SquadronScopeService squadronScopeService;
  private final AuthHelperService authHelperService;
  private final JobOrderMapper jobOrderMapper;
  private final de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper inventoryItemMapper;

  /**
   * Persists a new job order from the create DTO. The next available priority slot is taken
   * automatically (priority 1 is highest); minimum quality defaults to {@link #MIN_QUALITY_DEFAULT}
   * for any material without an explicit value.
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

    Squadron creatingSquadron = resolveCreatingSquadronForCreate(createDto.creatingSquadronId());
    Squadron requestingSquadron =
        resolveRequestingSquadron(
            createDto.requestingSquadronId(), createDto.squadron(), creatingSquadron);

    JobOrder jobOrder =
        JobOrder.builder()
            .handle(createDto.handle())
            .priority(newPriority)
            .creatingSquadron(creatingSquadron)
            .requestingSquadron(requestingSquadron)
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
              .minQuality(MIN_QUALITY_DEFAULT)
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
   * Paged list with optional status filter. Status is the primary discriminator the UI offers as a
   * filter; without it the call returns every status.
   *
   * <p>Delegates to {@link #getAllJobOrders(List, UUID, Pageable)} with a {@code null} squadron
   * filter for backwards compatibility — existing callers (and admin views in "all squadrons" mode)
   * keep their cross-staffel result set.
   *
   * @param statuses optional status filter; null/empty means "all"
   * @param pageable page request
   * @return paged job orders as DTOs
   */
  public Page<JobOrderDto> getAllJobOrders(List<JobOrderStatus> statuses, Pageable pageable) {
    return getAllJobOrders(statuses, null, pageable);
  }

  /**
   * Paged list with optional status + squadron filters. Job Orders are a cross-staffel workspace
   * (MULTI_SQUADRON_PLAN.md section 4.4) so the squadron filter is a UI display preference, not an
   * access-control gate — the list endpoint accepts the parameter so the frontend can default the
   * orders-index page to "my squadron only" (matching either {@code creatingSquadron} OR {@code
   * requestingSquadron} per section 5.3) while still allowing the user to flip back to "all
   * squadrons".
   *
   * @param statuses optional status filter; null/empty means "all"
   * @param squadronId optional squadron filter (matches creating OR requesting); null means "no
   *     squadron restriction"
   * @param pageable page request
   * @return paged job orders as DTOs
   */
  public Page<JobOrderDto> getAllJobOrders(
      List<JobOrderStatus> statuses, UUID squadronId, Pageable pageable) {
    if (statuses == null || statuses.isEmpty()) {
      if (squadronId == null) {
        return jobOrderRepository.findAll(pageable).map(this::mapToDtoWithStock);
      }
      return jobOrderRepository
          .findBySquadronInvolved(squadronId, pageable)
          .map(this::mapToDtoWithStock);
    }
    return jobOrderRepository
        .findByStatusInAndSquadronInvolved(statuses, squadronId, pageable)
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
                    // Wire-shape compatibility: the legacy `squadron` DTO field is fed from
                    // requestingSquadron.shorthand now that JobOrder.squadron (entity field) was
                    // removed in this commit. Clients on the v1 contract still see the same
                    // string until V89 drops the DTO field entirely in the next-but-one release.
                    o.getRequestingSquadron() != null
                        ? o.getRequestingSquadron().getShorthand()
                        : null,
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

    // creatingSquadron is immutable post-create (MULTI_SQUADRON_PLAN.md section 8 + section 11
    // expectation that a creatingSquadronId change rejects with 400/409). Reject explicitly so
    // misbehaving clients learn about the contract instead of silently dropping the field. The
    // legacy fallback resolution remains for pre-V83 rows that have no creator stamp yet — in
    // that case the column is filled in for the first time, which counts as initial stamping
    // rather than a mutation of an existing value.
    Squadron existingCreator = jobOrder.getCreatingSquadron();
    if (existingCreator != null
        && updateDto.creatingSquadronId() != null
        && !existingCreator.getId().equals(updateDto.creatingSquadronId())) {
      throw new BadRequestException(
          "creatingSquadronId is immutable post-create; pass null or the unchanged id.");
    }
    Squadron creatingFallback =
        existingCreator != null ? existingCreator : resolveCreatingSquadronForCreate(null);
    Squadron newRequesting =
        resolveRequestingSquadron(
            updateDto.requestingSquadronId(), updateDto.squadron(), creatingFallback);
    jobOrder.setRequestingSquadron(newRequesting);
    jobOrder.setHandle(updateDto.handle());

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
              .minQuality(MIN_QUALITY_DEFAULT)
              .amount(matDto.amount())
              .build();

      jobOrder.addMaterial(jobOrderMaterial);
    }

    jobOrder = jobOrderRepository.save(jobOrder);
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
                  return new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto(
                      matDto.id(),
                      matDto.material(),
                      matDto.minQuality(),
                      matDto.amount(),
                      stock != null ? stock : 0.0,
                      matDto.version());
                })
            .toList();

    return new JobOrderDto(
        baseDto.id(),
        baseDto.displayId(),
        baseDto.squadron(),
        baseDto.creatingSquadron(),
        baseDto.requestingSquadron(),
        baseDto.handle(),
        baseDto.priority(),
        baseDto.status(),
        updatedMaterials,
        baseDto.assignees(),
        baseDto.handovers(),
        baseDto.createdAt(),
        baseDto.version());
  }

  /**
   * Resolves the {@code creating_squadron_id} stamp for a freshly-created job order.
   *
   * <p>Resolution order (MULTI_SQUADRON_PLAN.md section 4.4):
   *
   * <ol>
   *   <li>Explicit {@code creatingSquadronIdOverride} from the create DTO — only honored when the
   *       caller is an admin (admin override path).
   *   <li>Caller's active squadron context ({@link SquadronScopeService#currentSquadron()}).
   *   <li>Admin in "all squadrons" mode without override → {@link BadRequestException} (400). The
   *       Plan is explicit on this: a focused stamp is required so the column is populated
   *       correctly for the V86 NOT NULL tightening; silently falling back to IRIDIUM would mask a
   *       real misconfiguration.
   * </ol>
   *
   * @param creatingSquadronIdOverride optional explicit value from the create DTO; rejected for
   *     non-admins.
   * @return the resolved squadron; never {@code null}.
   * @throws BadRequestException when the caller is an admin in all-squadrons mode and did not
   *     supply an explicit {@code creatingSquadronId}, or when a non-admin tried to override.
   */
  @org.jetbrains.annotations.NotNull
  private Squadron resolveCreatingSquadronForCreate(
      @org.jetbrains.annotations.Nullable UUID creatingSquadronIdOverride) {
    if (creatingSquadronIdOverride != null) {
      if (!authHelperService.isAdmin()) {
        throw new BadRequestException(
            "Only admins may set creatingSquadronId explicitly; non-admins are stamped from"
                + " their active squadron context.");
      }
      return squadronRepository
          .findById(creatingSquadronIdOverride)
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "creatingSquadronId does not resolve to a known squadron: "
                          + creatingSquadronIdOverride));
    }
    java.util.Optional<Squadron> active = squadronScopeService.currentSquadron();
    if (active.isPresent()) {
      return active.orElseThrow();
    }
    // No active context AND no explicit override. Plan-compliant 400: admin in all-squadrons
    // mode must pick a creator explicitly so the audit trail stays meaningful.
    throw new BadRequestException(
        "No active squadron context — admins in 'all squadrons' mode must supply"
            + " creatingSquadronId explicitly when creating a job order.");
  }

  /**
   * Resolves the {@code requesting_squadron_id} for create / update.
   *
   * <p>Preference order:
   *
   * <ol>
   *   <li>Typed {@code requestingSquadronId} UUID from the DTO (the new, plan-compliant path).
   *   <li>Legacy free-text {@code squadron} shorthand string (backwards compat for clients that
   *       have not migrated yet — resolved against {@code squadron.shorthand} case-insensitively).
   *   <li>Fallback to {@code creatingFallback} (the creating squadron) so the requesting field
   *       stays populated even on minimal payloads. Mirrors the V83 backfill rule.
   * </ol>
   *
   * @param requestingSquadronId typed UUID from the DTO; preferred when present.
   * @param squadronText legacy free-text fallback from the DTO; resolved against shorthand.
   * @param creatingFallback the creating squadron used as the ultimate fallback.
   * @return the resolved squadron; never {@code null}.
   */
  @org.jetbrains.annotations.NotNull
  private Squadron resolveRequestingSquadron(
      @org.jetbrains.annotations.Nullable UUID requestingSquadronId,
      @org.jetbrains.annotations.Nullable String squadronText,
      @org.jetbrains.annotations.NotNull Squadron creatingFallback) {
    if (requestingSquadronId != null) {
      return squadronRepository
          .findById(requestingSquadronId)
          .orElseThrow(
              () ->
                  new BadRequestException(
                      "requestingSquadronId does not resolve to a known squadron: "
                          + requestingSquadronId));
    }
    if (squadronText != null && !squadronText.isBlank()) {
      return squadronRepository.findByShorthand(squadronText.trim()).orElse(creatingFallback);
    }
    return creatingFallback;
  }
}
