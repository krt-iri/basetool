package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.JobOrderHandoverMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemCreateDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderHandoverRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobOrderHandoverService {

    private final JobOrderRepository jobOrderRepository;
    private final JobOrderHandoverRepository jobOrderHandoverRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final JobOrderHandoverMapper jobOrderHandoverMapper;

    @Transactional
    public JobOrderHandoverDto createHandover(UUID jobOrderId, JobOrderHandoverCreateDto dto) {
        JobOrder jobOrder = jobOrderRepository.findById(jobOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JobOrder not found"));

        JobOrderHandover handover = new JobOrderHandover();
        handover.setJobOrder(jobOrder);
        handover.setHandoverTime(dto.handoverTime());
        handover.setRecipientHandle(dto.recipientHandle());
        handover.setRecipientSquadron(dto.recipientSquadron());

        for (JobOrderHandoverItemCreateDto itemDto : dto.items()) {
            InventoryItem inventoryItem = inventoryItemRepository.findByIdForUpdate(itemDto.inventoryItemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));

            if (inventoryItem.getJobOrder() == null || !inventoryItem.getJobOrder().getId().equals(jobOrderId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inventory item does not belong to this JobOrder");
            }

            if (itemDto.amount() > inventoryItem.getAmount() + 0.0001) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot hand over more than the available amount");
            }

            double remainingAmount = inventoryItem.getAmount() - itemDto.amount();

            JobOrderHandoverItem handoverItem = new JobOrderHandoverItem();
            handoverItem.setInventoryItem(inventoryItem);
            handoverItem.setMaterial(inventoryItem.getMaterial());
            handoverItem.setQuality(inventoryItem.getQuality());
            handoverItem.setAmount(itemDto.amount());

            handover.addItem(handoverItem);

            if (remainingAmount <= 0.0001) {
                inventoryItemRepository.delete(inventoryItem);
            } else {
                inventoryItem.setAmount(remainingAmount);
                inventoryItemRepository.save(inventoryItem);
            }

            jobOrder.getMaterials().stream()
                    .filter(mat -> mat.getMaterial().getId().equals(inventoryItem.getMaterial().getId()))
                    .findFirst()
                    .ifPresent(mat -> {
                        double newAmount = mat.getAmount() - itemDto.amount();
                        mat.setAmount(Math.max(0.0, newAmount));
                    });
        }

        JobOrderHandover savedHandover = jobOrderHandoverRepository.save(handover);
        return jobOrderHandoverMapper.toDto(savedHandover);
    }
}
