package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.model.CheckoutType;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryItemService {

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

    @Transactional(readOnly = true)
    public Page<AggregatedInventoryDto> getAggregatedInventory(Pageable pageable) {
        return inventoryItemRepository.getAggregatedInventory(pageable)
                .map(obj -> new AggregatedInventoryDto(
                        materialMapper.toDto((Material) obj[0]),
                        obj[1] != null ? Math.round(((Number) obj[1]).doubleValue() * 100.0) / 100.0 : 0.0,
                        obj[2] != null ? ((Number) obj[2]).doubleValue() : 0.0
                ));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemDto> getInventoryByMaterial(UUID materialId, Pageable pageable) {
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        return inventoryItemRepository.findByMaterialAndPersonalFalse(material, pageable).map(inventoryItemMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemDto> getUserInventory(UUID userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return inventoryItemRepository.findByUser(user, pageable).map(inventoryItemMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto> getMyAggregatedInventory(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        List<InventoryItemDto> items = inventoryItemRepository.findByUser(user, Pageable.unpaged())
                .getContent().stream().map(inventoryItemMapper::toDto).toList();

        return aggregateInventoryItems(items);
    }

    @Transactional(readOnly = true)
    public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto> getAllAggregatedInventory(List<UUID> materialIds, Integer minQuality) {
        boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
        List<InventoryItemDto> items = inventoryItemRepository.findGlobalByFilters(hasMaterials, hasMaterials ? materialIds : null, minQuality, Pageable.unpaged())
                .getContent().stream().map(inventoryItemMapper::toDto).toList();

        return aggregateInventoryItems(items);
    }

    private List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto> aggregateInventoryItems(List<InventoryItemDto> items) {
        return items.stream()
            .collect(java.util.stream.Collectors.groupingBy(InventoryItemDto::material))
            .entrySet().stream()
            .map(entry -> {
                de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto mat = entry.getKey();
                List<InventoryItemDto> matItems = entry.getValue();
                
                matItems.sort(java.util.Comparator.comparing(InventoryItemDto::quality).reversed()
                        .thenComparing(i -> i.location().name())
                        .thenComparing(java.util.Comparator.comparing(InventoryItemDto::amount).reversed()));

                double totalAmount = 0.0;
                double qualitySum = 0.0;
                int maxQuality = 0;
                
                for (InventoryItemDto item : matItems) {
                    double amt = item.amount() != null ? item.amount() : 0.0;
                    int qual = item.quality() != null ? item.quality() : 0;
                    totalAmount += amt;
                    qualitySum += amt * qual;
                    if (qual > maxQuality) {
                        maxQuality = qual;
                    }
                }
                
                double avgQuality = totalAmount > 0 ? qualitySum / totalAmount : 0.0;
                avgQuality = Math.round(avgQuality * 100.0) / 100.0;

                return new de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto(mat, totalAmount, avgQuality, maxQuality, matItems);
            })
            .sorted(java.util.Comparator.comparing(g -> g.material().name()))
            .toList();
    }
    
    @Transactional(readOnly = true)
    public Page<InventoryItemDto> getAllInventory(List<UUID> materialIds, Integer minQuality, Pageable pageable) {
        boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
        return inventoryItemRepository.findGlobalByFilters(hasMaterials, hasMaterials ? materialIds : null, minQuality, pageable)
                .map(inventoryItemMapper::toDto);
    }

    @Transactional
    public InventoryItemDto createInventoryItem(InventoryItemCreateDto dto, UUID currentUserId, boolean isAdmin) {
        UUID targetUserId = dto.userId() != null ? dto.userId() : currentUserId;
        if (!targetUserId.equals(currentUserId) && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to create inventory items for other users");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Material material = materialRepository.findById(dto.materialId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        Location location = locationRepository.findById(dto.locationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));

        InventoryItem item = new InventoryItem();
        item.setUser(user);
        item.setMaterial(material);
        item.setLocation(location);
        item.setQuality(dto.quality());
        item.setAmount(dto.amount());
        item.setPersonal(dto.personal() != null ? dto.personal() : false);

        if (Boolean.TRUE.equals(item.getPersonal()) && (dto.missionId() != null || dto.jobOrderId() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Personal items cannot be assigned to a mission or job order");
        }

        if (dto.missionId() != null) {
            Mission mission = missionRepository.findById(dto.missionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));
            item.setMission(mission);
        }

        if (dto.jobOrderId() != null) {
            JobOrder jobOrder = jobOrderRepository.findById(dto.jobOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found"));
            item.setJobOrder(jobOrder);
        }

        return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
    }

    @Transactional
    public void bookOutInventoryItem(UUID id, InventoryItemBookOutDto dto, UUID currentUserId, boolean isAdmin) {
        InventoryItem item = inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

        if (dto.version() != null && item.getVersion() != null && !item.getVersion().equals(dto.version())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(InventoryItem.class, id);
        }

        if (!item.getUser().getId().equals(currentUserId) && !isAdmin) {
            throw new AccessDeniedException("You are not allowed to book out this inventory item");
        }

        if (dto.amount() > item.getAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot book out more than the available amount");
        }

        CheckoutType checkoutType = dto.type();
        if (checkoutType == null) {
            checkoutType = (dto.targetUserId() != null || dto.targetLocationId() != null) ? CheckoutType.TRANSFER : CheckoutType.DISCARD;
        }

        if (checkoutType == CheckoutType.SELL) {
            if (dto.terminal() == null || dto.terminal().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Terminal is required for selling");
            }
            if (dto.sellAmount() == null || dto.sellAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sell amount is required and must be positive");
            }
        }

        double remainingAmount = item.getAmount() - dto.amount();

        if (checkoutType == CheckoutType.TRANSFER && (dto.targetUserId() != null || dto.targetLocationId() != null)) {
            User targetUser = item.getUser();
            if (dto.targetUserId() != null && !dto.targetUserId().equals(item.getUser().getId())) {
                targetUser = userRepository.findById(dto.targetUserId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found"));
            }

            Location targetLocation = item.getLocation();
            if (dto.targetLocationId() != null && !dto.targetLocationId().equals(item.getLocation().getId())) {
                targetLocation = locationRepository.findById(dto.targetLocationId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target location not found"));
            }

            if (targetUser.getId().equals(item.getUser().getId()) && targetLocation.getId().equals(item.getLocation().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer must change either the user or the location");
            }

            InventoryItem newItem = new InventoryItem();
            newItem.setUser(targetUser);
            newItem.setMaterial(item.getMaterial());
            newItem.setLocation(targetLocation);
            newItem.setQuality(item.getQuality());
            newItem.setAmount(dto.amount());
            newItem.setPersonal(item.getPersonal());
            newItem.setJobOrder(item.getJobOrder());
            newItem.setMission(item.getMission());
            inventoryItemRepository.save(newItem);
        } else if (checkoutType == CheckoutType.SELL && item.getMission() != null) {
            MissionParticipant participant = missionParticipantRepository.findByMissionIdAndUserId(item.getMission().getId(), currentUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "You must be a participant of the mission to sell its items"));

            MissionFinanceEntry entry = new MissionFinanceEntry();
            entry.setMission(item.getMission());
            entry.setParticipant(participant);
            entry.setType(FinanceType.INCOME);
            entry.setAmount(dto.sellAmount());
            entry.setNote("Sale of " + dto.amount() + "x " + item.getMaterial().getName() + " at " + dto.terminal());
            missionFinanceEntryRepository.save(entry);
        }

        if (remainingAmount <= 0.0001) { // Floating point precision safety
            inventoryItemRepository.delete(item);
        } else {
            item.setAmount(remainingAmount);
            inventoryItemRepository.save(item);
        }
    }
}
