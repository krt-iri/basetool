package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreItemDto;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefineryOrderService {

    private final RefineryOrderRepository refineryOrderRepository;
    private final UserRepository userRepository;
    private final LocationRepository locationRepository;
    private final MissionRepository missionRepository;
    private final RefiningMethodRepository refiningMethodRepository;
    private final MaterialRepository materialRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final JobOrderRepository jobOrderRepository;

    public Page<RefineryOrder> getMyRefineryOrders(@NotNull UUID userId, List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses, @NotNull Pageable pageable) {
        if (statuses != null && !statuses.isEmpty()) {
            return refineryOrderRepository.findByOwnerIdAndStatusIn(userId, statuses, pageable);
        }
        return refineryOrderRepository.findByOwnerId(userId, pageable);
    }

    public Page<RefineryOrder> getMyRefineryOrders(@NotNull UUID userId, @NotNull Pageable pageable) {
        return refineryOrderRepository.findByOwnerId(userId, pageable);
    }

    public List<RefineryOrder> getMissionRefineryOrders(@NotNull UUID missionId) {
        return refineryOrderRepository.findByMissionId(missionId);
    }

    public List<RefineryOrder> getMissionRefineryOrders(@NotNull UUID missionId, @NotNull UUID userId) {
        return refineryOrderRepository.findByMissionIdAndOwnerId(missionId, userId);
    }

    public Page<RefineryOrder> getAllRefineryOrders(List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses, @NotNull Pageable pageable) {
        if (statuses != null && !statuses.isEmpty()) {
            return refineryOrderRepository.findByStatusIn(statuses, pageable);
        }
        return refineryOrderRepository.findAll(pageable);
    }

    public Page<RefineryOrder> getAllRefineryOrders(@NotNull Pageable pageable) {
        return refineryOrderRepository.findAll(pageable);
    }

    public RefineryOrder getRefineryOrder(@NotNull UUID id) {
        return refineryOrderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("RefineryOrder not found"));
    }

    @Transactional
    public RefineryOrder createRefineryOrder(@NotNull UUID userId, @NotNull RefineryOrder order) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        order.setOwner(user);

        if (order.getLocation() != null && order.getLocation().getId() != null) {
            order.setLocation(locationRepository.findById(order.getLocation().getId())
                .orElseThrow(() -> new RuntimeException("Location not found")));
            validateLocationHasRefinery(order.getLocation());
        } else {
            throw new RuntimeException("Location is required");
        }

        if (order.getMission() != null && order.getMission().getId() != null) {
            order.setMission(missionRepository.findById(order.getMission().getId())
                .orElseThrow(() -> new RuntimeException("Mission not found")));
        } else {
            order.setMission(null);
        }

        if (order.getRefiningMethod() != null && order.getRefiningMethod().getId() != null) {
            order.setRefiningMethod(refiningMethodRepository.findById(order.getRefiningMethod().getId())
                .orElseThrow(() -> new RuntimeException("RefiningMethod not found")));
        } else {
            order.setRefiningMethod(null);
        }

        // Handle Goods relationships
        if (order.getGoods() != null) {
            order.getGoods().forEach(good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material inMat = materialRepository.findById(good.getInputMaterial().getId())
                        .orElseThrow(() -> new RuntimeException("Input Material not found"));
                    
                    if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW) {
                        throw new IllegalArgumentException("Refinery goods input must be of type RAW. Material '" + inMat.getName() + "' is " + inMat.getType());
                    }
                    good.setInputMaterial(inMat);

                    if (good.getOutputMaterial() != null && good.getOutputMaterial().getId() != null) {
                        de.greluc.krt.iri.basetool.backend.model.Material outMat = materialRepository.findById(good.getOutputMaterial().getId())
                            .orElseThrow(() -> new RuntimeException("Output Material not found"));
                            
                        if (inMat.getRefinedMaterial() != null && !outMat.getId().equals(inMat.getRefinedMaterial().getId())) {
                            throw new IllegalArgumentException("Output material must match the refined material of the input material.");
                        }
                        
                        good.setOutputMaterial(outMat);
                    } else {
                        if (inMat.getRefinedMaterial() != null) {
                            good.setOutputMaterial(inMat.getRefinedMaterial());
                        } else {
                            good.setOutputMaterial(inMat);
                        }
                    }
                } else {
                    throw new RuntimeException("Input Material is required for refined goods");
                }
                good.setRefineryOrder(order);
            });
        }

        if (order.getStartedAt() == null) {
            order.setStartedAt(java.time.Instant.now());
        }

        return refineryOrderRepository.save(order);
    }

    @Transactional
    public RefineryOrder updateRefineryOrder(@NotNull UUID userId, @NotNull UUID orderId, @NotNull RefineryOrder details, boolean isLogistician) {
        RefineryOrder order = getRefineryOrder(orderId);

        if (details.getVersion() != null && !order.getVersion().equals(details.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(RefineryOrder.class, orderId);
        }

        if (!isLogistician && (order.getOwner() == null || order.getOwner().getId() == null || !order.getOwner().getId().equals(userId))) {
            throw new AccessDeniedException("Access denied: You do not own this refinery order");
        }

        if (details.getLocation() != null && details.getLocation().getId() != null) {
            order.setLocation(locationRepository.findById(details.getLocation().getId())
                .orElseThrow(() -> new RuntimeException("Location not found")));
            validateLocationHasRefinery(order.getLocation());
        }

        if (details.getMission() != null && details.getMission().getId() != null) {
            order.setMission(missionRepository.findById(details.getMission().getId())
                .orElseThrow(() -> new RuntimeException("Mission not found")));
        } else if (details.getMission() == null) {
             order.setMission(null);
        }

        if (details.getRefiningMethod() != null && details.getRefiningMethod().getId() != null) {
            order.setRefiningMethod(refiningMethodRepository.findById(details.getRefiningMethod().getId())
                .orElseThrow(() -> new RuntimeException("RefiningMethod not found")));
        } else if (details.getRefiningMethod() == null) {
            order.setRefiningMethod(null);
        }

        order.setStartedAt(details.getStartedAt() != null ? details.getStartedAt() : java.time.Instant.now());
        order.setDurationMinutes(details.getDurationMinutes());
        order.setExpenses(details.getExpenses());

        // Update goods
        if (details.getGoods() != null) {
            order.getGoods().clear();
            details.getGoods().forEach(good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material inMat = materialRepository.findById(good.getInputMaterial().getId())
                        .orElseThrow(() -> new RuntimeException("Input Material not found"));
                    
                    if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW) {
                        throw new IllegalArgumentException("Refinery goods input must be of type RAW. Material '" + inMat.getName() + "' is " + inMat.getType());
                    }
                    good.setInputMaterial(inMat);

                    if (good.getOutputMaterial() != null && good.getOutputMaterial().getId() != null) {
                        de.greluc.krt.iri.basetool.backend.model.Material outMat = materialRepository.findById(good.getOutputMaterial().getId())
                            .orElseThrow(() -> new RuntimeException("Output Material not found"));
                            
                        if (inMat.getRefinedMaterial() != null && !outMat.getId().equals(inMat.getRefinedMaterial().getId())) {
                            throw new IllegalArgumentException("Output material must match the refined material of the input material.");
                        }
                        
                        good.setOutputMaterial(outMat);
                    } else {
                        if (inMat.getRefinedMaterial() != null) {
                            good.setOutputMaterial(inMat.getRefinedMaterial());
                        } else {
                            good.setOutputMaterial(inMat);
                        }
                    }
                } else {
                    throw new RuntimeException("Input Material is required for refined goods");
                }
                good.setRefineryOrder(order);
                order.getGoods().add(good);
            });
        }

        return refineryOrderRepository.save(order);
    }

    @Transactional
    public void deleteRefineryOrder(@NotNull UUID userId, @NotNull UUID orderId, boolean isLogistician) {
        RefineryOrder order = getRefineryOrder(orderId);

        if (!isLogistician && (order.getOwner() == null || order.getOwner().getId() == null || !order.getOwner().getId().equals(userId))) {
            throw new AccessDeniedException("Access denied: You do not own this refinery order");
        }

        order.setStatus(de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.CANCELED);
        refineryOrderRepository.save(order);
    }

    @Transactional
    public void storeRefineryOrder(@NotNull UUID userId, @NotNull UUID orderId, @NotNull RefineryOrderStoreDto dto, boolean isLogistician) {
        RefineryOrder order = getRefineryOrder(orderId);

        if (!isLogistician && (order.getOwner() == null || order.getOwner().getId() == null || !order.getOwner().getId().equals(userId))) {
            throw new AccessDeniedException("Access denied: You do not own this refinery order");
        }

        for (RefineryOrderStoreItemDto itemDto : dto.items()) {
            de.greluc.krt.iri.basetool.backend.model.Material mat = materialRepository.findById(itemDto.materialId())
                .orElseThrow(() -> new RuntimeException("Material not found: " + itemDto.materialId()));
            
            de.greluc.krt.iri.basetool.backend.model.Location loc = locationRepository.findById(itemDto.locationId())
                .orElseThrow(() -> new RuntimeException("Location not found: " + itemDto.locationId()));
                
            InventoryItem item = new InventoryItem();
            if (itemDto.userId() != null) {
                User assignee = userRepository.findById(itemDto.userId())
                    .orElseThrow(() -> new RuntimeException("User not found: " + itemDto.userId()));
                item.setUser(assignee);
            } else {
                item.setUser(order.getOwner());
            }

            if (itemDto.jobOrderId() != null) {
                de.greluc.krt.iri.basetool.backend.model.JobOrder jobOrder = jobOrderRepository.findById(itemDto.jobOrderId())
                    .orElseThrow(() -> new RuntimeException("JobOrder not found: " + itemDto.jobOrderId()));
                item.setJobOrder(jobOrder);
            }

            item.setMaterial(mat);
            item.setLocation(loc);
            item.setQuality(itemDto.quality());
            item.setAmount(itemDto.amount());
            item.setMission(order.getMission());
            
            inventoryItemRepository.save(item);
        }

        order.setStatus(de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.COMPLETED);
        refineryOrderRepository.save(order);
    }

    private void validateLocationHasRefinery(de.greluc.krt.iri.basetool.backend.model.Location location) {
        boolean hasRefinery = false;
        if (location.getCity() != null && Boolean.TRUE.equals(location.getCity().getHasRefinery())) {
            hasRefinery = true;
        } else if (location.getSpaceStation() != null && Boolean.TRUE.equals(location.getSpaceStation().getHasRefinery())) {
            hasRefinery = true;
        }
        if (!hasRefinery) {
            throw new IllegalArgumentException("Selected location does not have a refinery.");
        }
    }
}
