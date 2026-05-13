package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationUpdateDto;
import de.greluc.krt.iri.basetool.backend.repository.OperationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus payout computation for the {@code operation} aggregate (one or more missions grouped
 * under a single umbrella).
 *
 * <p>Deletion intentionally does NOT cascade to missions — the missions stay alive as
 * operation-less rows so their participant / inventory / refinery history survives. The operation
 * table is small; no caching here, every method goes through the repository directly.
 *
 * <p>The status transition uses the state machine declared on {@code OperationStatus}; admins can
 * override the gate via {@code canOverrideStatus=true} (resolved at the controller boundary from
 * the {@code Authentication}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OperationService {

  private final OperationRepository operationRepository;

  /**
   * @param pageable page request
   * @return paged operation list
   */
  @Transactional(readOnly = true)
  public Page<Operation> getAllOperations(@NotNull Pageable pageable) {
    return operationRepository.findAll(pageable);
  }

  /**
   * @param id operation primary key
   * @return the operation
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional(readOnly = true)
  public Operation getOperationById(@NotNull UUID id) {
    return operationRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "Operation not found"));
  }

  /**
   * Persists a new operation.
   *
   * @param operation transient entity
   * @return the persisted operation
   */
  @Transactional
  public Operation createOperation(@NotNull Operation operation) {
    return operationRepository.save(operation);
  }

  /**
   * Updates an existing operation. Validates the optimistic-lock version and the status state
   * machine (unless the caller has the admin override).
   *
   * @param id operation primary key
   * @param updateDto update payload (carries the expected version + new status)
   * @param canOverrideStatus when true, the state-machine check is bypassed (admin/officer)
   * @return the persisted operation
   * @throws ObjectOptimisticLockingFailureException when the supplied version is stale
   * @throws BadRequestException when the status transition is invalid and override is not granted
   */
  @Transactional
  public Operation updateOperation(
      @NotNull UUID id, @NotNull OperationUpdateDto updateDto, boolean canOverrideStatus) {
    Operation operation =
        operationRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "Operation not found"));

    if (updateDto.version() != null && !operation.getVersion().equals(updateDto.version())) {
      throw new ObjectOptimisticLockingFailureException(Operation.class, id);
    }

    if (!canOverrideStatus && !operation.getStatus().canTransitionTo(updateDto.status())) {
      throw new BadRequestException(
          "Invalid operation status transition: "
              + operation.getStatus()
              + " -> "
              + updateDto.status());
    }

    operation.setName(updateDto.name());
    operation.setDescription(updateDto.description());
    operation.setStatus(updateDto.status());

    return operationRepository.save(operation);
  }

  /**
   * Deletes an operation without deleting its missions.
   *
   * <p>Each linked mission has its {@code operation} reference cleared (Hibernate dirty-checking
   * emits a single {@code UPDATE} per mission). The in-memory collection is cleared explicitly so
   * the bidirectional state stays consistent inside the transaction; the operation row is then
   * removed. Participants, finance entries, inventory items and refinery orders of the underlying
   * missions are untouched — this delete is purely a "ungroup" action.
   *
   * @param id operation primary key
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional
  public void deleteOperation(@NotNull UUID id) {
    log.info("Deleting operation with ID: {}", id);
    Operation operation =
        operationRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "Operation not found"));

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

  /**
   * Computes the per-participant time-share of an operation for payout splitting.
   *
   * <p>For each mission with valid {@code actualStartTime}/{@code actualEndTime}, the method
   * iterates participants and accumulates the duration of their attendance clamped to the mission
   * window. The percentage is the participant's clamped time divided by the operation's total
   * clamped time. {@link de.greluc.krt.iri.basetool.backend.model.PayoutPreference} {@code DONATE}
   * sticks once chosen (a single DONATE preference across all missions of the operation marks the
   * participant as donating). Output is sorted alphabetically.
   *
   * <p>Uses {@link
   * de.greluc.krt.iri.basetool.backend.repository.OperationRepository#findWithMissionsAndParticipantsById}
   * with an explicit {@code @EntityGraph} so the loop's {@code
   * .getMissions()/.getParticipants()/.getUser()} never trigger lazy SELECTs — would otherwise be
   * {@code 1 + N + N*M} round trips for {@code N} missions × {@code M} participants each.
   *
   * @param id operation primary key
   * @return per-participant payout shares, sorted by participant name
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  @Transactional(readOnly = true)
  public java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto>
      getOperationPayouts(@NotNull UUID id) {
    // Use the explicit fetch graph: the loop below touches missions,
    // participants and the participants' user reference. Without the
    // graph each level would trigger its own lazy SELECT, costing
    // 1 + N + (N*M) round-trips for N missions / M participants each.
    Operation operation =
        operationRepository
            .findWithMissionsAndParticipantsById(id)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "Operation not found"));

    java.util.Map<String, String> participantNames = new java.util.HashMap<>();
    java.util.Map<String, Long> validDurations = new java.util.HashMap<>();
    java.util.Map<String, de.greluc.krt.iri.basetool.backend.model.PayoutPreference> preferences =
        new java.util.HashMap<>();
    long totalOperationValidDuration = 0L;

    for (de.greluc.krt.iri.basetool.backend.model.Mission mission : operation.getMissions()) {
      java.time.Instant actualStart = mission.getActualStartTime();
      java.time.Instant actualEnd = mission.getActualEndTime();
      if (actualStart == null || actualEnd == null || !actualEnd.isAfter(actualStart)) {
        continue; // Mission didn't start/end properly
      }

      for (de.greluc.krt.iri.basetool.backend.model.MissionParticipant p :
          mission.getParticipants()) {
        String pId =
            p.getUser() != null
                ? p.getUser().getId().toString()
                : (p.getGuestName() != null ? "guest_" + p.getGuestName() : null);
        if (pId == null) continue;

        String pName = p.getUser() != null ? p.getUser().getEffectiveName() : p.getGuestName();
        participantNames.putIfAbsent(pId, pName);

        de.greluc.krt.iri.basetool.backend.model.PayoutPreference currentPref =
            preferences.getOrDefault(
                pId, de.greluc.krt.iri.basetool.backend.model.PayoutPreference.PAYOUT);
        if (p.getPayoutPreference()
            == de.greluc.krt.iri.basetool.backend.model.PayoutPreference.DONATE) {
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

    java.util.List<de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto> result =
        new java.util.ArrayList<>();
    for (String pId : participantNames.keySet()) {
      long duration = validDurations.getOrDefault(pId, 0L);
      double percentage =
          totalOperationValidDuration > 0
              ? (double) duration / totalOperationValidDuration * 100.0
              : 0.0;
      // Round to 2 decimals
      percentage = Math.round(percentage * 100.0) / 100.0;
      result.add(
          new de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto(
              pId, participantNames.get(pId), percentage, preferences.get(pId)));
    }

    result.sort(
        java.util.Comparator.comparing(
            de.greluc.krt.iri.basetool.backend.model.dto.OperationPayoutDto::participantName,
            String.CASE_INSENSITIVE_ORDER));
    return result;
  }
}
