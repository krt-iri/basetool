package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobOrderService {

    /**
     * Default minimum quality assigned to every {@link de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial}
     * when a job order is created or updated without an explicit minimum on the wire.
     * 750 is the conventional refining-grade floor used across the squadron's existing
     * orders; it is hard-coded for now so the value lives in code review rather than in
     * a database row that can drift silently. If the squadron starts adjusting this per
     * order, promote it to a {@code SystemSetting} entry.
     */
    private static final int MIN_QUALITY_DEFAULT = 750;

    private final JobOrderRepository jobOrderRepository;
    private final MaterialRepository materialRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserRepository userRepository;
    private final JobOrderMapper jobOrderMapper;
    private final de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper inventoryItemMapper;

    @Transactional
    public JobOrderDto createJobOrder(CreateJobOrderDto createDto) {
        jobOrderRepository.lockAllJobOrders();
        Integer newPriority = jobOrderRepository.findMaxPriority().orElse(0) + 1;

        JobOrder jobOrder = JobOrder.builder()
                .squadron(createDto.squadron())
                .handle(createDto.handle())
                .priority(newPriority)
                .build();

        for (CreateJobOrderMaterialDto matDto : createDto.materials()) {
            Material material = materialRepository.findById(matDto.materialId())
                    .orElseThrow(() -> new NotFoundException("Material not found: " + matDto.materialId()));

            JobOrderMaterial jobOrderMaterial = JobOrderMaterial.builder()
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

    public Page<JobOrderDto> getAllJobOrders(List<JobOrderStatus> statuses, Pageable pageable) {
        if (statuses == null || statuses.isEmpty()) {
            return jobOrderRepository.findAll(pageable).map(this::mapToDtoWithStock);
        }
        return jobOrderRepository.findByStatusIn(statuses, pageable).map(this::mapToDtoWithStock);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto> findAllActiveReference() {
        return jobOrderRepository.findAllActiveWithMaterials()
                .stream()
                .map(o -> new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto(
                        o.getId(),
                        o.getDisplayId(),
                        o.getSquadron(),
                        o.getHandle(),
                        o.getStatus(),
                        o.getMaterials() != null ? o.getMaterials().stream().map(jobOrderMapper::toDto).toList() : List.of()
                ))
                .toList();
    }

    public JobOrderDto getJobOrderById(UUID id) {
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));
        return mapToDtoWithStock(jobOrder);
    }

    public List<de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto> getInventoryItemsForJobOrderMaterial(UUID jobOrderId, UUID materialId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException("Material not found: " + materialId));

        return inventoryItemRepository.findByJobOrderIdAndMaterialId(jobOrderId, materialId).stream()
                .map(inventoryItemMapper::toDto)
                .sorted(java.util.Comparator
                        .comparing((de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto item) ->
                                item.user() != null && item.user().effectiveName() != null ? item.user().effectiveName() : "",
                                java.util.Comparator.naturalOrder())
                        .thenComparing(item -> item.quality() != null ? item.quality() : 0,
                                java.util.Comparator.reverseOrder())
                        .thenComparing(item -> item.location() != null && item.location().name() != null ? item.location().name() : "",
                                java.util.Comparator.naturalOrder())
                        .thenComparing(item -> item.amount() != null ? item.amount() : 0.0,
                                java.util.Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * Updates the status of a JobOrder. This method performs its own {@code findById()} +
     * {@code save()} + {@code flush()} and therefore MUST only be called from a context where
     * the target {@link JobOrder} entity is NOT already managed/dirty in the current persistence
     * context (i.e. called directly from a Controller, not from within another
     * {@code @Transactional} service method that has already modified the same entity).
     *
     * <p><strong>WARNING:</strong> Calling this method from within a running transaction that has
     * already modified the same {@code JobOrder} (e.g. via cascade after
     * {@code jobOrderHandoverRepository.save()}) will cause a double-save that collides with
     * the already-incremented {@code @Version} field, resulting in an
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} (HTTP 409).
     * Use {@link #completeJobOrderWithinTransaction(JobOrder)} instead in such cases.</p>
     */
    @Transactional
    public JobOrderDto updateJobOrderStatus(UUID id, UpdateJobOrderStatusDto dto) {
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

        if (dto.version() != null && jobOrder.getVersion() != null && !jobOrder.getVersion().equals(dto.version())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(JobOrder.class, id);
        }

        JobOrderStatus status = dto.status();
        boolean isTerminal = (status == JobOrderStatus.COMPLETED || status == JobOrderStatus.REJECTED);
        boolean wasTerminal = (jobOrder.getStatus() == JobOrderStatus.COMPLETED || jobOrder.getStatus() == JobOrderStatus.REJECTED);

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

    @Transactional
    public JobOrderDto updateJobOrderPriority(UUID id, Integer newPriority) {
        JobOrder targetOrder = jobOrderRepository.findById(id)
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
        
        List<JobOrder> activeOrders = new java.util.ArrayList<>(allOrders.stream()
                .filter(o -> o.getPriority() != null)
                .sorted(java.util.Comparator.comparing(JobOrder::getPriority).thenComparing(JobOrder::getCreatedAt))
                .toList());

        activeOrders.remove(targetOrder);
        
        int newIndex = newPriority - 1;
        if (newIndex < 0) newIndex = 0;
        if (newIndex > activeOrders.size()) newIndex = activeOrders.size();
        
        activeOrders.add(newIndex, targetOrder);
        
        int currentPrio = 1;
        for (JobOrder o : activeOrders) {
            o.setPriority(currentPrio++);
        }

        return mapToDtoWithStock(targetOrder);
    }

    @Transactional
    public JobOrderDto updateJobOrder(UUID id, CreateJobOrderDto updateDto) {
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

        if (updateDto.version() != null && jobOrder.getVersion() != null && !jobOrder.getVersion().equals(updateDto.version())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(JobOrder.class, id);
        }

        jobOrder.setSquadron(updateDto.squadron());
        jobOrder.setHandle(updateDto.handle());

        List<UUID> newMaterialIds = updateDto.materials().stream()
                .map(CreateJobOrderMaterialDto::materialId)
                .toList();

        List<UUID> removedMaterialIds = jobOrder.getMaterials().stream()
                .map(mat -> mat.getMaterial().getId())
                .filter(matId -> !newMaterialIds.contains(matId))
                .toList();

        for (UUID removedId : removedMaterialIds) {
            inventoryItemRepository.unlinkJobOrderMaterial(jobOrder.getId(), removedId);
        }

        jobOrder.getMaterials().clear();

        for (CreateJobOrderMaterialDto matDto : updateDto.materials()) {
            Material material = materialRepository.findById(matDto.materialId())
                    .orElseThrow(() -> new NotFoundException("Material not found: " + matDto.materialId()));

            JobOrderMaterial jobOrderMaterial = JobOrderMaterial.builder()
                    .material(material)
                    .minQuality(MIN_QUALITY_DEFAULT)
                    .amount(matDto.amount())
                    .build();

            jobOrder.addMaterial(jobOrderMaterial);
        }

        jobOrder = jobOrderRepository.save(jobOrder);
        return mapToDtoWithStock(jobOrder);
    }

    @Transactional
    public void deleteJobOrder(UUID id) {
        jobOrderRepository.lockAllJobOrders();
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + id));

        Integer priority = jobOrder.getPriority();
        inventoryItemRepository.unlinkJobOrder(id);
        jobOrderRepository.delete(jobOrder);
        jobOrderRepository.flush();
        if (priority != null) {
            normalizePriorities();
        }
    }

    @Transactional
    public JobOrderDto addAssignee(UUID jobOrderId, UUID userId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        jobOrder.getAssignees().add(user);
        return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
    }

    @Transactional
    public void unlinkMaterial(UUID jobOrderId, UUID materialId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));

        boolean exists = jobOrder.getMaterials().stream()
                .anyMatch(m -> m.getMaterial().getId().equals(materialId));
        if (!exists) {
            throw new NotFoundException("Material not linked to job order: " + materialId);
        }

        inventoryItemRepository.unlinkJobOrderMaterial(jobOrderId, materialId);

        jobOrder.getMaterials().removeIf(m -> m.getMaterial().getId().equals(materialId));
        jobOrderRepository.save(jobOrder);
    }

    @Transactional
    public void unlinkInventoryItem(UUID jobOrderId, UUID inventoryItemId) {
        jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));

        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new NotFoundException("InventoryItem not found: " + inventoryItemId));

        if (item.getJobOrder() == null || !item.getJobOrder().getId().equals(jobOrderId)) {
            throw new NotFoundException("InventoryItem not linked to job order: " + inventoryItemId);
        }

        item.setJobOrder(null);
    }

    @Transactional
    public JobOrderDto removeAssignee(UUID jobOrderId, UUID userId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
        jobOrder.getAssignees().remove(user);
        return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
    }

    /**
     * Marks a JobOrder as COMPLETED within an already-running transaction.
     * This method MUST be called from within an active transaction (e.g. from
     * {@code JobOrderHandoverService}) so that the passed {@code jobOrder} entity
     * is already managed by the current persistence context.  Using the managed
     * entity directly avoids the double-save / optimistic-lock conflict that
     * occurs when {@link #updateJobOrderStatus} is called with its own
     * {@code findById} inside the caller's transaction.
     *
     * @param jobOrder the managed {@link JobOrder} entity to complete
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeJobOrderWithinTransaction(JobOrder jobOrder) {
        boolean wasTerminal = (jobOrder.getStatus() == JobOrderStatus.COMPLETED
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
        List<JobOrder> activeOrders = jobOrderRepository.lockAllJobOrders().stream()
                .filter(o -> o.getPriority() != null)
                .sorted(java.util.Comparator.comparing(JobOrder::getPriority).thenComparing(JobOrder::getCreatedAt))
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
        
        List<de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto> updatedMaterials = baseDto.materials().stream().map(matDto -> {
            Double stock = inventoryItemRepository.sumAmountByMaterialAndJobOrderAndMinQuality(matDto.material().id(), jobOrder.getId(), matDto.minQuality());
            log.debug("Stock for job order #{} (ID: {}), material {}: {} / required: {} (min quality: {})", 
                jobOrder.getDisplayId(), jobOrder.getId(), matDto.material().name(), stock, matDto.amount(), matDto.minQuality());
            return new de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto(
                matDto.id(),
                matDto.material(),
                matDto.minQuality(),
                matDto.amount(),
                stock != null ? stock : 0.0,
                matDto.version()
            );
        }).toList();
        
        return new JobOrderDto(baseDto.id(), baseDto.displayId(), baseDto.squadron(), baseDto.handle(), baseDto.priority(), baseDto.status(), updatedMaterials, baseDto.assignees(), baseDto.handovers(), baseDto.createdAt(), baseDto.version());
    }
}