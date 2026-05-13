package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.BusinessConflictException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus aggregation for mission finance entries (income / expense rows attached to a
 * participant).
 *
 * <p>The total-sum aggregation also folds in the profit/loss of refinery orders linked to the
 * mission (ore sales minus expenses minus other expenses). Refinery orders surface here because the
 * mission finance page is the single source of truth for a mission's bottom line — splitting
 * refinery-derived profit into its own page would force users to mentally combine two numbers.
 *
 * <p>Update and delete are gated by {@code @PreAuthorize} on {@link
 * MissionSecurityService#canEditFinanceEntry} — admins/officers can edit any entry, the
 * participant's user can edit only their own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionFinanceEntryService {

  private final MissionFinanceEntryRepository financeEntryRepository;
  private final MissionParticipantRepository participantRepository;
  private final MissionRepository missionRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository
      refineryOrderRepository;
  private final MissionMapper missionMapper;

  /**
   * @param missionId mission id
   * @param pageable page request
   * @return paged finance entries for the mission
   */
  @Transactional(readOnly = true)
  public Page<MissionFinanceEntryDto> getEntriesByMission(UUID missionId, Pageable pageable) {
    return financeEntryRepository.findAllByMissionId(missionId, pageable).map(missionMapper::toDto);
  }

  /**
   * Aggregated bottom line for a mission: finance entries (income minus expense) plus refinery
   * order profit. Legacy refinery rows with null sales/expenses are treated as 0 — early data
   * pre-dates the column.
   *
   * @param missionId mission id
   * @return signed total in mission credits
   */
  @Transactional(readOnly = true)
  public BigDecimal calculateTotalSum(UUID missionId) {
    List<MissionFinanceEntry> entries = financeEntryRepository.findAllByMissionId(missionId);
    BigDecimal total = BigDecimal.ZERO;
    for (MissionFinanceEntry entry : entries) {
      if (entry.getType() == FinanceType.INCOME) {
        total = total.add(entry.getAmount());
      } else if (entry.getType() == FinanceType.EXPENSE) {
        total = total.subtract(entry.getAmount());
      }
    }

    // Refinery orders now contribute their profit/loss (oreSales - expenses - otherExpenses)
    // rather than only their costs. Null values are treated as 0 (legacy-data safety).
    List<RefineryOrder> refineryOrders = refineryOrderRepository.findByMissionId(missionId);
    for (RefineryOrder order : refineryOrders) {
      double sales = order.getOreSales() != null ? order.getOreSales() : 0d;
      double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
      double otherCosts = order.getOtherExpenses() != null ? order.getOtherExpenses() : 0d;
      double profit = sales - costs - otherCosts;
      if (profit != 0d) {
        total = total.add(BigDecimal.valueOf(profit));
      }
    }

    return total;
  }

  /**
   * Creates a finance entry. The participant must belong to the named mission; mismatched
   * participant + mission pair surfaces as a 400 {@link BadRequestException} rather than a 500 —
   * distinguishes "bad inputs" from "server bug" for client error handling.
   *
   * @param dto create payload
   * @return the persisted entry
   * @throws NotFoundException when the mission or participant id does not resolve
   * @throws BadRequestException when the participant belongs to a different mission
   */
  @Transactional
  public MissionFinanceEntryDto createEntry(MissionFinanceEntryCreateDto dto) {
    Mission mission =
        missionRepository
            .findById(dto.missionId())
            .orElseThrow(() -> new NotFoundException("Mission not found"));
    MissionParticipant participant =
        participantRepository
            .findById(dto.participantId())
            .orElseThrow(() -> new NotFoundException("Assigned participant not found"));

    if (!participant.getMission().getId().equals(mission.getId())) {
      throw new BadRequestException("Participant does not belong to this mission");
    }

    MissionFinanceEntry entry =
        MissionFinanceEntry.builder()
            .mission(mission)
            .participant(participant)
            .note(dto.note())
            .type(dto.type())
            .amount(dto.amount())
            .build();

    entry = financeEntryRepository.save(entry);
    return missionMapper.toDto(entry);
  }

  /**
   * Updates an existing finance entry. Optimistic-lock check is explicit (the DTO carries the
   * expected version) and surfaces as {@link BusinessConflictException} → 409 rather than Spring's
   * automatic {@code ObjectOptimisticLockingFailureException}: this entity is only mutated through
   * this service, so explicit checks keep the error path readable.
   *
   * @param entryId finance entry id
   * @param dto update payload (carries the expected version)
   * @return the persisted entry
   * @throws NotFoundException when the entry does not exist
   * @throws BusinessConflictException when the supplied version no longer matches
   */
  @Transactional
  @PreAuthorize("@missionSecurityService.canEditFinanceEntry(#entryId, authentication)")
  public MissionFinanceEntryDto updateEntry(UUID entryId, MissionFinanceEntryUpdateDto dto) {
    MissionFinanceEntry entry =
        financeEntryRepository
            .findById(entryId)
            .orElseThrow(() -> new NotFoundException("Finance entry not found"));

    // Optimistic Locking Check
    if (!entry.getVersion().equals(dto.version())) {
      throw new BusinessConflictException(
          "The entry has been updated by someone else. Please reload.");
    }

    entry.setNote(dto.note());
    entry.setType(dto.type());
    entry.setAmount(dto.amount());

    entry = financeEntryRepository.save(entry);
    return missionMapper.toDto(entry);
  }

  /**
   * Deletes a finance entry.
   *
   * @param entryId finance entry id
   * @throws NotFoundException when the entry does not exist
   */
  @Transactional
  @PreAuthorize("@missionSecurityService.canEditFinanceEntry(#entryId, authentication)")
  public void deleteEntry(UUID entryId) {
    MissionFinanceEntry entry =
        financeEntryRepository
            .findById(entryId)
            .orElseThrow(() -> new NotFoundException("Finance entry not found"));

    financeEntryRepository.delete(entry);
  }
}
