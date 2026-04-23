package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.JobOrderMapper;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderMaterialDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobOrderService {

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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found: " + matDto.materialId()));

            JobOrderMaterial jobOrderMaterial = JobOrderMaterial.builder()
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

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public JobOrderDto getJobOrderById(UUID id) {
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + id));
        return mapToDtoWithStock(jobOrder);
    }

    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto> getInventoryItemsForJobOrderMaterial(UUID jobOrderId, UUID materialId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + jobOrderId));
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found: " + materialId));

        return inventoryItemRepository.findByJobOrderIdAndMaterialId(jobOrderId, materialId).stream()
                .map(inventoryItemMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public JobOrderDto updateJobOrderStatus(UUID id, JobOrderStatus status) {
        JobOrder jobOrder = jobOrderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + id));

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + id));

        Integer oldPriority = targetOrder.getPriority();
        if (oldPriority == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot update priority of a completed or rejected job order");
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + id));

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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found: " + matDto.materialId()));

            JobOrderMaterial jobOrderMaterial = JobOrderMaterial.builder()
                    .material(material)
                    .minQuality(matDto.minQuality())
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + id));

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + jobOrderId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
        jobOrder.getAssignees().add(user);
        return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
    }

    @Transactional
    public JobOrderDto removeAssignee(UUID jobOrderId, UUID userId) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found: " + jobOrderId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));
        jobOrder.getAssignees().remove(user);
        return mapToDtoWithStock(jobOrderRepository.save(jobOrder));
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