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
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("RefineryOrder not found"));
    }

    @Transactional
    public RefineryOrder createRefineryOrder(@NotNull UUID userId, @NotNull RefineryOrder order) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found"));
        
        order.setOwner(user);

        if (order.getLocation() != null && order.getLocation().getId() != null) {
            order.setLocation(locationRepository.findById(order.getLocation().getId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Location not found")));
            validateLocationHasRefinery(order.getLocation());
        } else {
            throw new RuntimeException("Location is required");
        }

        if (order.getMission() != null && order.getMission().getId() != null) {
            order.setMission(missionRepository.findById(order.getMission().getId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Mission not found")));
        } else {
            order.setMission(null);
        }

        if (order.getRefiningMethod() != null && order.getRefiningMethod().getId() != null) {
            order.setRefiningMethod(refiningMethodRepository.findById(order.getRefiningMethod().getId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("RefiningMethod not found")));
        } else {
            order.setRefiningMethod(null);
        }

        // Handle Goods relationships
        if (order.getGoods() != null) {
            order.getGoods().forEach(good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material inMat = materialRepository.findById(good.getInputMaterial().getId())
                        .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Input Material not found"));
                    
                    if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW && !Boolean.TRUE.equals(inMat.getIsManualRawMaterial())) {
                        throw new IllegalArgumentException("Refinery goods input must be of type RAW. Material '" + inMat.getName() + "' is " + inMat.getType());
                    }
                    good.setInputMaterial(inMat);

                    if (good.getOutputMaterial() != null && good.getOutputMaterial().getId() != null) {
                        de.greluc.krt.iri.basetool.backend.model.Material outMat = materialRepository.findById(good.getOutputMaterial().getId())
                            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Output Material not found"));
                            
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

        // Geldfelder (expenses, otherExpenses, oreSales) sind optional. Sowohl null als auch 0
        // werden semantisch als "nicht gesetzt" behandelt und persistiert als null. Damit muss
        // das Frontend nicht zwischen "leer" und "0" unterscheiden, und die Spalten bleiben in
        // der DB sauber leer, wenn der Nutzer keinen Wert eingegeben hat. Die Profit-Berechnung
        // (siehe RefineryOrder#getProfit) behandelt null bereits als 0.
        order.setExpenses(zeroToNull(order.getExpenses()));
        order.setOtherExpenses(zeroToNull(order.getOtherExpenses()));
        order.setOreSales(zeroToNull(order.getOreSales()));

        return refineryOrderRepository.save(order);
    }

    private static Double zeroToNull(Double value) {
        if (value == null) return null;
        return value == 0.0 ? null : value;
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
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Location not found")));
            validateLocationHasRefinery(order.getLocation());
        }

        if (details.getMission() != null && details.getMission().getId() != null) {
            order.setMission(missionRepository.findById(details.getMission().getId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Mission not found")));
        } else if (details.getMission() == null) {
             order.setMission(null);
        }

        if (details.getRefiningMethod() != null && details.getRefiningMethod().getId() != null) {
            order.setRefiningMethod(refiningMethodRepository.findById(details.getRefiningMethod().getId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("RefiningMethod not found")));
        } else if (details.getRefiningMethod() == null) {
            order.setRefiningMethod(null);
        }

        order.setStartedAt(details.getStartedAt() != null ? details.getStartedAt() : java.time.Instant.now());
        order.setDurationMinutes(details.getDurationMinutes());
        // Geldfelder: 0 wird wie "nicht gesetzt" behandelt und als null persistiert (siehe createRefineryOrder).
        order.setExpenses(zeroToNull(details.getExpenses()));
        order.setOtherExpenses(zeroToNull(details.getOtherExpenses()));
        order.setOreSales(zeroToNull(details.getOreSales()));
        if (details.getStatus() != null) {
            order.setStatus(details.getStatus());
        }

        // Update goods
        if (details.getGoods() != null) {
            order.getGoods().clear();
            details.getGoods().forEach(good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material inMat = materialRepository.findById(good.getInputMaterial().getId())
                        .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Input Material not found"));
                    
                    if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW && !Boolean.TRUE.equals(inMat.getIsManualRawMaterial())) {
                        throw new IllegalArgumentException("Refinery goods input must be of type RAW. Material '" + inMat.getName() + "' is " + inMat.getType());
                    }
                    good.setInputMaterial(inMat);

                    if (good.getOutputMaterial() != null && good.getOutputMaterial().getId() != null) {
                        de.greluc.krt.iri.basetool.backend.model.Material outMat = materialRepository.findById(good.getOutputMaterial().getId())
                            .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Output Material not found"));
                            
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

        if (order.getStatus() == de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.COMPLETED) {
            throw new IllegalStateException("Refinery order is already completed and stored.");
        }

        if (!isLogistician && (order.getOwner() == null || order.getOwner().getId() == null || !order.getOwner().getId().equals(userId))) {
            throw new AccessDeniedException("Access denied: You do not own this refinery order");
        }

        for (RefineryOrderStoreItemDto itemDto : dto.items()) {
            de.greluc.krt.iri.basetool.backend.model.Material mat = materialRepository.findById(itemDto.materialId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Material not found: " + itemDto.materialId()));
            
            de.greluc.krt.iri.basetool.backend.model.Location loc = locationRepository.findById(itemDto.locationId())
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Location not found: " + itemDto.locationId()));
                
            User assignee;
            if (itemDto.userId() != null) {
                assignee = userRepository.findById(itemDto.userId())
                    .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("User not found: " + itemDto.userId()));
            } else {
                assignee = order.getOwner();
            }

            de.greluc.krt.iri.basetool.backend.model.JobOrder jobOrder = null;
            if (itemDto.jobOrderId() != null) {
                jobOrder = jobOrderRepository.findById(itemDto.jobOrderId())
                    .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("JobOrder not found: " + itemDto.jobOrderId()));
            }

            java.util.List<InventoryItem> existingItems = inventoryItemRepository.findMatchingInventoryItem(
                assignee,
                mat,
                loc,
                itemDto.quality(),
                order.getMission(),
                jobOrder,
                false
            );

            java.util.Optional<InventoryItem> existingItemOpt = existingItems.stream().findFirst();

            // Warum: Die vom Nutzer im Einlagern-Dialog eingegebene Menge ist die
            // autoritative Menge (sie ueberschreibt die urspruenglich berechnete
            // Ausgangsmenge des Raffinerieauftrags). Die Notiz wird optional an das
            // resultierende InventoryItem durchgereicht, damit der Nutzer Anmerkungen
            // zur Einlagerung direkt an das Lagergut binden kann.
            String incomingNote = normalizeNote(itemDto.note());

            if (existingItemOpt.isPresent()) {
                InventoryItem existingItem = existingItemOpt.get();
                existingItem.setAmount(existingItem.getAmount() + itemDto.amount());
                if (incomingNote != null) {
                    String existingNote = existingItem.getNote();
                    if (existingNote == null || existingNote.isBlank()) {
                        existingItem.setNote(incomingNote);
                    } else if (!existingNote.contains(incomingNote)) {
                        // Bestehende Notiz beibehalten und neue anhaengen, damit keine
                        // Information verloren geht.
                        String combined = existingNote + "\n" + incomingNote;
                        if (combined.length() > 1000) {
                            combined = combined.substring(0, 1000);
                        }
                        existingItem.setNote(combined);
                    }
                }
                inventoryItemRepository.save(existingItem);
            } else {
                InventoryItem item = new InventoryItem();
                item.setUser(assignee);
                item.setJobOrder(jobOrder);
                item.setMaterial(mat);
                item.setLocation(loc);
                item.setQuality(itemDto.quality());
                item.setAmount(itemDto.amount());
                item.setMission(order.getMission());
                item.setNote(incomingNote);

                inventoryItemRepository.save(item);
            }

            // Angepasste Menge zurueck in den Raffinerieauftrag schreiben, damit die
            // tatsaechlich eingelagerte Ausgangsmenge auch dort dokumentiert ist.
            // Match ueber das Output-Material; bei mehreren gleichen Goods wird der
            // erste noch nicht aktualisierte Eintrag genommen.
            updateGoodOutputQuantity(order, itemDto);
        }

        order.setStatus(de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.COMPLETED);
        refineryOrderRepository.save(order);
    }

    private static String normalizeNote(String note) {
        if (note == null) return null;
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Schreibt die vom Nutzer final eingegebene Einlager-Menge zurueck in den
     * zugehoerigen {@link de.greluc.krt.iri.basetool.backend.model.RefineryGood}.
     * So wird die manuelle Korrektur der Ausgangsmenge im Raffinerieauftrag selbst
     * persistiert (z. B. wenn der tatsaechliche Raffinerie-Output von der Prognose
     * abweicht). Bei SCU-Materialien wird die SCU-Eingabe in Units (x100) zurueck-
     * konvertiert, damit das Feld {@code outputQuantity} einheitlich in Units gefuehrt bleibt.
     */
    private void updateGoodOutputQuantity(RefineryOrder order, RefineryOrderStoreItemDto itemDto) {
        if (order.getGoods() == null || itemDto == null || itemDto.materialId() == null || itemDto.amount() == null) {
            return;
        }
        for (de.greluc.krt.iri.basetool.backend.model.RefineryGood good : order.getGoods()) {
            if (good.getOutputMaterial() == null || good.getOutputMaterial().getId() == null) continue;
            if (!good.getOutputMaterial().getId().equals(itemDto.materialId())) continue;

            double amount = itemDto.amount();
            String qType = good.getOutputMaterial().getQuantityType() != null
                ? good.getOutputMaterial().getQuantityType().name()
                : null;
            long rawNew;
            if ("SCU".equals(qType)) {
                rawNew = Math.round(amount * 100.0d);
            } else {
                rawNew = Math.round(amount);
            }
            // @Min(1) auf outputQuantity respektieren: 0 waere ein invalider Wert.
            int clamped = (int) Math.max(1L, Math.min(rawNew, Integer.MAX_VALUE));
            good.setOutputQuantity(clamped);
            return;
        }
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
