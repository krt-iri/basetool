package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.MissionMapper;
import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionFinanceEntryService {

    private final MissionFinanceEntryRepository financeEntryRepository;
    private final MissionParticipantRepository participantRepository;
    private final MissionRepository missionRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository refineryOrderRepository;
    private final MissionMapper missionMapper;

    @Transactional(readOnly = true)
    public Page<MissionFinanceEntryDto> getEntriesByMission(UUID missionId, Pageable pageable) {
        return financeEntryRepository.findAllByMissionId(missionId, pageable)
                .map(missionMapper::toDto);
    }

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
        
        // Raffinerieauftraege fliessen nun mit ihrem Gewinn/Verlust (oreSales - expenses) ein,
        // nicht mehr nur mit ihren Kosten. Null-Werte werden als 0 behandelt (Altdaten-Schutz).
        List<RefineryOrder> refineryOrders = refineryOrderRepository.findByMissionId(missionId);
        for (RefineryOrder order : refineryOrders) {
            double sales = order.getOreSales() != null ? order.getOreSales() : 0d;
            double costs = order.getExpenses() != null ? order.getExpenses() : 0d;
            double profit = sales - costs;
            if (profit != 0d) {
                total = total.add(BigDecimal.valueOf(profit));
            }
        }
        
        return total;
    }

    @Transactional
    public MissionFinanceEntryDto createEntry(MissionFinanceEntryCreateDto dto) {
        Mission mission = missionRepository.findById(dto.missionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mission not found"));
        MissionParticipant participant = participantRepository.findById(dto.participantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assigned participant not found"));

        if (!participant.getMission().getId().equals(mission.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant does not belong to this mission");
        }

        MissionFinanceEntry entry = MissionFinanceEntry.builder()
                .mission(mission)
                .participant(participant)
                .note(dto.note())
                .type(dto.type())
                .amount(dto.amount())
                .build();

        entry = financeEntryRepository.save(entry);
        return missionMapper.toDto(entry);
    }

    @Transactional
    @PreAuthorize("@missionSecurityService.canEditFinanceEntry(#entryId, authentication)")
    public MissionFinanceEntryDto updateEntry(UUID entryId, MissionFinanceEntryUpdateDto dto) {
        MissionFinanceEntry entry = financeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finance entry not found"));

        // Optimistic Locking Check
        if (!entry.getVersion().equals(dto.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The entry has been updated by someone else. Please reload.");
        }

        entry.setNote(dto.note());
        entry.setType(dto.type());
        entry.setAmount(dto.amount());

        entry = financeEntryRepository.save(entry);
        return missionMapper.toDto(entry);
    }

    @Transactional
    @PreAuthorize("@missionSecurityService.canEditFinanceEntry(#entryId, authentication)")
    public void deleteEntry(UUID entryId) {
        MissionFinanceEntry entry = financeEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finance entry not found"));

        financeEntryRepository.delete(entry);
    }

}