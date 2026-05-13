package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OperationService {

    private final OperationRepository operationRepository;

    public Page<Operation> getAllOperations(@NotNull Pageable pageable) {
        return operationRepository.findAll(pageable);
    }

    public Operation getOperationById(@NotNull UUID id) {
        return operationRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Operation not found"));
    }

    @Transactional
    public Operation createOperation(@NotNull Operation operation) {
        return operationRepository.save(operation);
    }

    @Transactional
    public Operation updateOperation(@NotNull UUID id, @NotNull OperationUpdateDto updateDto, boolean canOverrideStatus) {
        Operation operation = operationRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Operation not found"));

        if (updateDto.version() != null && !operation.getVersion().equals(updateDto.version())) {
            throw new ObjectOptimisticLockingFailureException(Operation.class, id);
        }

        if (!canOverrideStatus && !operation.getStatus().canTransitionTo(updateDto.status())) {
            throw new BadRequestException("Invalid operation status transition: "
                    + operation.getStatus() + " -> " + updateDto.status());
        }

        operation.setName(updateDto.name());
        operation.setDescription(updateDto.description());
        operation.setStatus(updateDto.status());

        return operationRepository.save(operation);
    }

    @Transactional
    public void deleteOperation(@NotNull UUID id) {
        log.info("Deleting operation with ID: {}", id);
        Operation operation = operationRepository.findById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Operation not found"));

        // Unlink missions instead of cascading the delete. The mission itself,
        // its participants, finance entries, inventory items and refinery orders
        // all stay intact — only the operation_id back-reference is cleared so
        // the rows can survive as operation-less missions.
        for (de.greluc.krt.iri.basetool.backend.model.Mission mission : operation.getMissions()) {
            mission.setOperation(null);
        }
        operation.getMissions().clear();

        operationRepository.delete(operation);
        log.info("Successfully deleted operation with ID: {}", id);
    }

    public java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto> getOperationPayouts(@NotNull UUID id) {
        // Use the explicit fetch graph: the loop below touches missions,
        // participants and the participants' user reference. Without the
        // graph each level would trigger its own lazy SELECT, costing
        // 1 + N + (N*M) round-trips for N missions / M participants each.
        Operation operation = operationRepository.findWithMissionsAndParticipantsById(id)
                .orElseThrow(() -> new de.greluc.krt.iri.basetool.backend.exception.NotFoundException("Operation not found"));

        java.util.Map<String, String> participantNames = new java.util.HashMap<>();
        java.util.Map<String, Long> validDurations = new java.util.HashMap<>();
        java.util.Map<String, de.greluc.krt.iri.basetool.backend.model.PayoutPreference> preferences = new java.util.HashMap<>();
        long totalOperationValidDuration = 0L;

        for (de.greluc.krt.iri.basetool.backend.model.Mission mission : operation.getMissions()) {
            java.time.Instant actualStart = mission.getActualStartTime();
            java.time.Instant actualEnd = mission.getActualEndTime();
            if (actualStart == null || actualEnd == null || !actualEnd.isAfter(actualStart)) {
                continue; // Mission didn't start/end properly
            }

            for (de.greluc.krt.iri.basetool.backend.model.MissionParticipant p : mission.getParticipants()) {
                String pId = p.getUser() != null ? p.getUser().getId().toString() : (p.getGuestName() != null ? "guest_" + p.getGuestName() : null);
                if (pId == null) continue;

                String pName = p.getUser() != null ? p.getUser().getEffectiveName() : p.getGuestName();
                participantNames.putIfAbsent(pId, pName);

                de.greluc.krt.iri.basetool.backend.model.PayoutPreference currentPref = preferences.getOrDefault(pId, de.greluc.krt.iri.basetool.backend.model.PayoutPreference.PAYOUT);
                if (p.getPayoutPreference() == de.greluc.krt.iri.basetool.backend.model.PayoutPreference.DONATE) {
                    preferences.put(pId, de.greluc.krt.iri.basetool.backend.model.PayoutPreference.DONATE);
                } else {
                    preferences.putIfAbsent(pId, currentPref);
                }

                java.time.Instant pStart = p.getStartTime();
                if (pStart == null) continue;
                
                java.time.Instant pEnd = p.getEndTime() != null ? p.getEndTime() : java.time.Instant.now();

                java.time.Instant effectiveStart = pStart.isBefore(actualStart) ? actualStart : pStart;
                java.time.Instant effectiveEnd = pEnd.isAfter(actualEnd) ? actualEnd : pEnd;

                if (effectiveEnd.isAfter(effectiveStart)) {
                    long duration = java.time.Duration.between(effectiveStart, effectiveEnd).toMillis();
                    validDurations.put(pId, validDurations.getOrDefault(pId, 0L) + duration);
                    totalOperationValidDuration += duration;
                }
            }
        }

        java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto> result = new java.util.ArrayList<>();
        for (String pId : participantNames.keySet()) {
            long duration = validDurations.getOrDefault(pId, 0L);
            double percentage = totalOperationValidDuration > 0 ? (double) duration / totalOperationValidDuration * 100.0 : 0.0;
            // Round to 2 decimals
            percentage = Math.round(percentage * 100.0) / 100.0;
            result.add(new de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto(
                    pId,
                    participantNames.get(pId),
                    percentage,
                    preferences.get(pId)
            ));
        }
        
        result.sort(java.util.Comparator.comparing(de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto::participantName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}