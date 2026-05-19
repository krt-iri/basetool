package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.JobOrderHandoverMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemCreateDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderMaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service handling JobOrderHandoverService operations. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderHandoverService {

  /**
   * Tolerance used when comparing handover / inventory quantities that are stored as {@code
   * double}. Quantities are user-edited and rounded to the displayed precision (three decimals) on
   * the way in, so any residual below 1e-4 is floating-point noise rather than a real surplus /
   * deficit. Picked an order of magnitude below the smallest representable user quantity to keep
   * the rounding-safe comparison cheap.
   */
  private static final double QUANTITY_EPSILON = 1e-4;

  private final JobOrderRepository jobOrderRepository;
  private final JobOrderHandoverRepository jobOrderHandoverRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final JobOrderHandoverMapper jobOrderHandoverMapper;
  private final JobOrderMaterialRepository jobOrderMaterialRepository;
  private final JobOrderService jobOrderService;
  private final UserService userService;

  /**
   * Creates a JobOrder handover and atomically applies the resulting effects:
   *
   * <ul>
   *   <li>reduces inventory amounts (or deletes the row when fully consumed),
   *   <li>reduces the open amount per {@link
   *       de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial},
   *   <li>persists the handover plus its items,
   *   <li>unlinks remaining inventory rows for materials that are now fully fulfilled,
   *   <li>completes the JobOrder if all materials are fulfilled.
   * </ul>
   *
   * <p><b>Concurrency / Optimistic Locking:</b> The previous implementation issued the bulk {@code
   * unlinkJobOrderMaterial} update <em>inside</em> the per-item loop. The bulk update carries
   * {@code @Modifying(clearAutomatically = true, flushAutomatically = true)} which (1) flushes
   * pending changes mid-iteration and (2) detaches the {@link JobOrder} aggregate (and all of its
   * {@link de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial}s) from the persistence
   * context. Subsequent loop iterations then operated on detached entities and called {@code
   * jobOrderMaterialRepository.save(mat)} which silently triggers an {@code EntityManager.merge()}.
   * Combined with the cascade on {@code JobOrder.materials} and the additional {@code
   * findById}/{@code save}/{@code flush} cycle in {@code
   * JobOrderService.completeJobOrderWithinTransaction()} this produced a second version-bump on
   * already-modified rows — a textbook {@link
   * org.springframework.orm.ObjectOptimisticLockingFailureException} (HTTP 409) when an entire
   * JobOrder was fulfilled by a single multi-material handover.
   *
   * <p>The structural fix (extension of the {@code *WithinTransaction} pattern documented in {@code
   * AGENTS.md} to the {@code JobOrderMaterial} / {@code Stock} aggregates) consists of three rules
   * enforced below:
   *
   * <ol>
   *   <li>The loop only mutates managed entities — it relies on Hibernate's dirty checking and
   *       never calls {@code jobOrderMaterialRepository.save(mat)} explicitly.
   *   <li>Bulk updates with {@code clearAutomatically=true} are deferred until <em>after</em> the
   *       loop has completed and the new handover has been persisted.
   *   <li>The {@link JobOrder} is re-fetched <em>once</em> after the bulk updates so the completion
   *       check operates on a freshly managed aggregate (with up-to-date {@code @Version}s).
   * </ol>
   */
  @Transactional
  public JobOrderHandoverDto createHandover(UUID jobOrderId, JobOrderHandoverCreateDto dto) {
    JobOrder jobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found"));

    JobOrderHandover handover = new JobOrderHandover();
    handover.setJobOrder(jobOrder);
    handover.setHandoverTime(dto.handoverTime());
    handover.setRecipientHandle(dto.recipientHandle());
    handover.setRecipientSquadron(dto.recipientSquadron());

    // Audit trail: capture the executing user + their squadron snapshot at handover time.
    // Cross-staffel workspace (MULTI_SQUADRON_PLAN.md section 4.4) means the executing user may
    // belong to a different squadron than the order's owning one — without this stamp the audit
    // trail does not record who actually performed the write on a foreign squadron's items.
    userService
        .getCurrentUser()
        .ifPresent(
            current -> {
              handover.setExecutingUser(current);
              handover.setExecutingSquadron(current.getSquadron());
            });

    // Materials whose remaining open amount drops to (effectively) zero in this handover.
    // The corresponding inventory rows are unlinked AFTER the loop so that no
    // {@code clearAutomatically=true} bulk update detaches the aggregate mid-iteration.
    Set<UUID> materialsToUnlink = new HashSet<>();

    for (JobOrderHandoverItemCreateDto itemDto : dto.items()) {
      // The InventoryItem is only used as a transient lookup source for the snapshot data
      // (material, quality) and to update / delete the source inventory row. It is intentionally
      // NOT referenced from JobOrderHandoverItem anymore so that emptying the inventory does not
      // break historical handover records (see CHANGELOG / V64 migration).
      InventoryItem inventoryItem =
          inventoryItemRepository
              .findByIdForUpdate(itemDto.inventoryItemId())
              .orElseThrow(() -> new NotFoundException("Inventory item not found"));

      if (inventoryItem.getJobOrder() == null
          || !inventoryItem.getJobOrder().getId().equals(jobOrderId)) {
        // Plan §4.4 cross-staffel pre-write guard: the handover may only mutate inventory items
        // that are bound to the current order via job_order_id. A mismatch means either a stale
        // client payload or a concurrent unlink — in both cases the handover cannot proceed and
        // the application is in an inconsistent state for this request. GlobalExceptionHandler
        // maps IllegalStateException to 400 so the wire format stays the same as before.
        throw new IllegalStateException("Inventory item does not belong to this JobOrder");
      }

      if (itemDto.amount() > inventoryItem.getAmount() + QUANTITY_EPSILON) {
        throw new BadRequestException("Cannot hand over more than the available amount");
      }
      QuantityType quantityType =
          inventoryItem.getMaterial() != null
              ? inventoryItem.getMaterial().getQuantityType()
              : null;
      if (quantityType == QuantityType.PIECE && itemDto.amount() % 1 != 0) {
        throw new BadRequestException("Amount must be a whole number for PIECE materials");
      }

      final double remainingAmount = inventoryItem.getAmount() - itemDto.amount();

      JobOrderHandoverItem handoverItem = new JobOrderHandoverItem();
      handoverItem.setMaterial(inventoryItem.getMaterial());
      handoverItem.setQuality(inventoryItem.getQuality());
      handoverItem.setAmount(itemDto.amount());
      handoverItem.setLocationName(
          inventoryItem.getLocation() != null ? inventoryItem.getLocation().getName() : null);

      handover.addItem(handoverItem);

      if (remainingAmount <= QUANTITY_EPSILON) {
        inventoryItemRepository.delete(inventoryItem);
      } else {
        inventoryItem.setAmount(remainingAmount);
        inventoryItemRepository.save(inventoryItem);
      }

      // Mutate the managed JobOrderMaterial via dirty checking. We MUST NOT call
      // jobOrderMaterialRepository.save(mat) here: a save() on a detached entity (which
      // would happen if a previous iteration's bulk update detached the aggregate) silently
      // performs a merge() and produces a second version-bump on the same row, leading to
      // ObjectOptimisticLockingFailureException at commit time.
      jobOrder.getMaterials().stream()
          .filter(mat -> mat.getMaterial().getId().equals(inventoryItem.getMaterial().getId()))
          .findFirst()
          .ifPresent(
              mat -> {
                double newAmount = mat.getAmount() - itemDto.amount();
                mat.setAmount(Math.max(0.0, newAmount));
                if (mat.getAmount() <= QUANTITY_EPSILON) {
                  materialsToUnlink.add(mat.getMaterial().getId());
                }
              });
    }

    // Persist the handover (incl. items via cascade) BEFORE issuing any bulk update that would
    // clear the persistence context. This guarantees the handover row exists by the time the
    // bulk UPDATEs run and avoids implicit re-merge of detached entities.
    JobOrderHandover savedHandover = jobOrderHandoverRepository.save(handover);
    // Capture the DTO while the entity graph is still attached and fully initialised.
    JobOrderHandoverDto resultDto = jobOrderHandoverMapper.toDto(savedHandover);

    // Run the (potentially session-clearing) bulk unlinks ONCE per fulfilled material, AFTER
    // all per-item bookkeeping is done.  Each call carries
    // {@code @Modifying(clearAutomatically=true, flushAutomatically=true)} which flushes the
    // pending dirty changes from the loop and then detaches the persistence context; doing
    // this once at the end (instead of inside the loop) is what makes the multi-material
    // completion flow safe with respect to optimistic locking.
    for (UUID materialId : materialsToUnlink) {
      inventoryItemRepository.unlinkJobOrderMaterial(jobOrderId, materialId);
    }

    // Re-fetch the JobOrder so the completion check runs on a freshly managed aggregate
    // with up-to-date {@code @Version}s. The previous bulk unlinks (and any auto-flush) have
    // already detached the original {@code jobOrder} reference from the session.
    JobOrder managedJobOrder =
        jobOrderRepository
            .findById(jobOrderId)
            .orElseThrow(() -> new NotFoundException("JobOrder not found"));

    boolean allFulfilled =
        managedJobOrder.getMaterials().stream()
            .allMatch(mat -> mat.getAmount() <= QUANTITY_EPSILON);

    if (allFulfilled) {
      // Use the dedicated WithinTransaction method that works on the already-managed entity
      // to avoid the double-save / optimistic-locking conflict that would otherwise occur if
      // {@code updateJobOrderStatus()} performed its own findById() + save() + flush() inside
      // the running transaction (see {@code AGENTS.md} — "INTRA-TRANSACTION SERVICE CALLS").
      jobOrderService.completeJobOrderWithinTransaction(managedJobOrder);
    }

    return resultDto;
  }
}
