/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.OrgChartPositionMapperImpl;
import de.greluc.krt.iri.basetool.backend.model.OrgChartPosition;
import de.greluc.krt.iri.basetool.backend.model.OrgChartPositionType;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.CommandChartDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OrgChartPositionUpdateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronChartDto;
import de.greluc.krt.iri.basetool.backend.repository.OrgChartPositionRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Mockito unit tests for {@link OrgChartService}. The real generated {@link
 * OrgChartPositionMapperImpl} is wired in (not a mock) so the nested-tree assembly is asserted on
 * concrete node values; the three repositories are mocked. Pins the read assembly (grouping by
 * scope / type / parent, the inline Kommando leader, the {@code canAdd*} flags) and every write
 * guard (scope/type consistency, parent rules, cardinality limits, the name / nullable-holder
 * rules, one-user-per-scope, optimistic lock).
 */
@ExtendWith(MockitoExtension.class)
class OrgChartServiceTest {

  @Mock private OrgChartPositionRepository positionRepository;
  @Mock private OrgUnitRepository orgUnitRepository;
  @Mock private UserRepository userRepository;

  private OrgChartService service() {
    return new OrgChartService(
        positionRepository, orgUnitRepository, userRepository, new OrgChartPositionMapperImpl());
  }

  // ------------------------------------------------------------------ read assembly --

  @Test
  void getOrgChart_assemblesAreaSquadronAndSkIntoNestedTree() {
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    SpecialCommand sk = specialCommand(UUID.randomUUID(), "Alpha SK", "ASK");
    when(orgUnitRepository.findActiveProfitEligible()).thenReturn(List.of(squadron, sk));
    OrgChartPosition areaLead = pos(OrgChartPositionType.AREA_LEAD, null, null);
    OrgChartPosition areaCoordinator = pos(OrgChartPositionType.AREA_COORDINATOR, null, null);
    when(positionRepository.findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc())
        .thenReturn(List.of(areaLead, areaCoordinator));

    OrgChartPosition squadronLead = pos(OrgChartPositionType.SQUADRON_LEAD, squadron, null);
    OrgChartPosition commandLead = pos(OrgChartPositionType.COMMAND_LEAD, squadron, null);
    OrgChartPosition deputy = pos(OrgChartPositionType.DEPUTY_COMMAND_LEAD, squadron, commandLead);
    OrgChartPosition commandEnsign = pos(OrgChartPositionType.ENSIGN, squadron, commandLead);
    OrgChartPosition directEnsign = pos(OrgChartPositionType.ENSIGN, squadron, null);
    OrgChartPosition skCommander = pos(OrgChartPositionType.SK_COMMANDER, sk, null);
    when(positionRepository.findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(any()))
        .thenReturn(
            List.of(squadronLead, commandLead, deputy, commandEnsign, directEnsign, skCommander));

    OrgChartDto chart = service().getOrgChart();

    assertNotNull(chart.areaLeadership().lead());
    assertEquals(OrgChartPositionType.AREA_LEAD, chart.areaLeadership().lead().positionType());
    assertEquals(1, chart.areaLeadership().coordinators().size());
    assertTrue(chart.areaLeadership().operators().isEmpty());
    assertTrue(chart.areaLeadership().commanders().isEmpty());

    assertEquals(1, chart.squadrons().size());
    SquadronChartDto squadronDto = chart.squadrons().getFirst();
    assertEquals("IRIDIUM", squadronDto.name());
    assertNotNull(squadronDto.lead());
    assertEquals(1, squadronDto.commands().size());
    CommandChartDto command = squadronDto.commands().getFirst();
    assertEquals(commandLead.getId(), command.positionId());
    assertNotNull(command.leaderUserId());
    assertNotNull(command.deputy());
    assertEquals(1, command.ensigns().size());
    assertEquals(1, squadronDto.directEnsigns().size());
    assertTrue(squadronDto.canAddCommand());
    assertTrue(squadronDto.canAddEnsign());

    assertEquals(1, chart.specialCommands().size());
    assertEquals(1, chart.specialCommands().getFirst().commanders().size());
    assertTrue(chart.specialCommands().getFirst().canAddCommander());
  }

  @Test
  void getOrgChart_leaderlessNamedCommand_carriesNameAndNullLeader() {
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    when(orgUnitRepository.findActiveProfitEligible()).thenReturn(List.of(squadron));
    when(positionRepository.findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc())
        .thenReturn(List.of());

    OrgChartPosition command = pos(OrgChartPositionType.COMMAND_LEAD, squadron, null, null);
    command.setName("Alpha");
    OrgChartPosition deputy = pos(OrgChartPositionType.DEPUTY_COMMAND_LEAD, squadron, command);
    when(positionRepository.findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(any()))
        .thenReturn(List.of(command, deputy));

    CommandChartDto dto = service().getOrgChart().squadrons().getFirst().commands().getFirst();

    assertEquals("Alpha", dto.name());
    assertNull(dto.leaderUserId(), "a leaderless Kommando exposes no holder");
    assertNull(dto.leaderUserName());
    assertNotNull(dto.deputy(), "a Stv. may hang off a leaderless Kommando");
  }

  @Test
  void getOrgChart_noProfitEligibleUnits_skipsUnitPositionQuery() {
    when(orgUnitRepository.findActiveProfitEligible()).thenReturn(List.of());
    when(positionRepository.findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc())
        .thenReturn(List.of());

    OrgChartDto chart = service().getOrgChart();

    assertNull(chart.areaLeadership().lead());
    assertTrue(chart.squadrons().isEmpty());
    assertTrue(chart.specialCommands().isEmpty());
    // The empty-IN guard must avoid the dialect-fragile IN () query entirely.
    verify(positionRepository, never()).findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(any());
  }

  @Test
  void getOrgChart_fullSquadron_clearsCanAddFlags() {
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    when(orgUnitRepository.findActiveProfitEligible()).thenReturn(List.of(squadron));
    when(positionRepository.findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc())
        .thenReturn(List.of());
    when(positionRepository.findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(any()))
        .thenReturn(
            List.of(
                pos(OrgChartPositionType.COMMAND_LEAD, squadron, null),
                pos(OrgChartPositionType.COMMAND_LEAD, squadron, null),
                pos(OrgChartPositionType.COMMAND_LEAD, squadron, null),
                pos(OrgChartPositionType.COMMAND_LEAD, squadron, null),
                pos(OrgChartPositionType.ENSIGN, squadron, null),
                pos(OrgChartPositionType.ENSIGN, squadron, null),
                pos(OrgChartPositionType.ENSIGN, squadron, null),
                pos(OrgChartPositionType.ENSIGN, squadron, null)));

    SquadronChartDto squadronDto = service().getOrgChart().squadrons().getFirst();

    assertEquals(4, squadronDto.commands().size());
    assertFalse(squadronDto.canAddCommand());
    assertFalse(squadronDto.canAddEnsign());
  }

  // ----------------------------------------------------------------- create guards --

  @Test
  void createPosition_areaCoordinator_persistsAndReturnsDto() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "coordinator")));
    when(positionRepository.existsByOrgUnitIsNullAndUserId(userId)).thenReturn(false);
    when(positionRepository.save(any()))
        .thenAnswer(
            inv -> {
              OrgChartPosition p = inv.getArgument(0);
              p.setId(UUID.randomUUID());
              return p;
            });

    OrgChartPositionDto dto =
        service()
            .createPosition(
                new OrgChartPositionCreateRequest(
                    OrgChartPositionType.AREA_COORDINATOR, null, userId, null, null, null));

    assertEquals(OrgChartPositionType.AREA_COORDINATOR, dto.positionType());
    assertEquals(userId, dto.userId());
    assertNull(dto.orgUnitId());
  }

  @Test
  void createPosition_commandLeadWithoutUser_createsLeaderlessNamedKommando() {
    UUID unitId = UUID.randomUUID();
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(squadron(unitId, "IRIDIUM", "IRI")));
    when(positionRepository.countByOrgUnitIdAndPositionType(
            unitId, OrgChartPositionType.COMMAND_LEAD))
        .thenReturn(0L);
    when(positionRepository.save(any()))
        .thenAnswer(
            inv -> {
              OrgChartPosition p = inv.getArgument(0);
              p.setId(UUID.randomUUID());
              return p;
            });

    OrgChartPositionDto dto =
        service()
            .createPosition(
                new OrgChartPositionCreateRequest(
                    OrgChartPositionType.COMMAND_LEAD, unitId, null, null, "  Alpha  ", null));

    assertEquals(OrgChartPositionType.COMMAND_LEAD, dto.positionType());
    assertNull(dto.userId(), "a Kommando may be created with no Kommandoleiter");
    assertEquals("Alpha", dto.name(), "the name is trimmed");
    verify(userRepository, never()).findById(any());
  }

  @Test
  void createPosition_nonCommandRankWithoutUser_isRejected() {
    UUID unitId = UUID.randomUUID();

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SQUADRON_LEAD, unitId, null, null, null, null)));

    assertTrue(ex.getMessage().contains("user_required"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void createPosition_nameOnNonCommandRank_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(squadron(unitId, "IRIDIUM", "IRI")));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SQUADRON_LEAD,
                            unitId,
                            userId,
                            null,
                            "Nope",
                            null)));

    assertTrue(ex.getMessage().contains("name_not_allowed"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void createPosition_deputyUnderLeaderlessKommando_persists() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    Squadron squadron = squadron(unitId, "IRIDIUM", "IRI");
    OrgChartPosition leaderlessKommando =
        pos(OrgChartPositionType.COMMAND_LEAD, squadron, null, null);
    leaderlessKommando.setId(parentId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "deputy")));
    when(orgUnitRepository.findById(unitId)).thenReturn(Optional.of(squadron));
    when(positionRepository.findById(parentId)).thenReturn(Optional.of(leaderlessKommando));
    when(positionRepository.existsByParentIdAndPositionType(
            parentId, OrgChartPositionType.DEPUTY_COMMAND_LEAD))
        .thenReturn(false);
    when(positionRepository.existsByOrgUnitIdAndUserId(unitId, userId)).thenReturn(false);
    when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrgChartPositionDto dto =
        service()
            .createPosition(
                new OrgChartPositionCreateRequest(
                    OrgChartPositionType.DEPUTY_COMMAND_LEAD,
                    unitId,
                    userId,
                    parentId,
                    null,
                    null));

    assertEquals(OrgChartPositionType.DEPUTY_COMMAND_LEAD, dto.positionType());
    assertEquals(userId, dto.userId());
  }

  @Test
  void createPosition_secondAreaLead_isRejected() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));
    when(positionRepository.existsByOrgUnitIsNullAndPositionType(OrgChartPositionType.AREA_LEAD))
        .thenReturn(true);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.AREA_LEAD, null, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("duplicate_lead"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void createPosition_squadronRankWithoutOrgUnit_isRejected() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SQUADRON_LEAD, null, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("scope_mismatch"));
  }

  @Test
  void createPosition_areaRankWithOrgUnit_isRejected() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.AREA_LEAD,
                            UUID.randomUUID(),
                            userId,
                            null,
                            null,
                            null)));

    assertTrue(ex.getMessage().contains("scope_mismatch"));
  }

  @Test
  void createPosition_unitKindMismatch_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));
    // SQUADRON_LEAD pointed at an SK row.
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(specialCommand(unitId, "Alpha SK", "ASK")));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SQUADRON_LEAD, unitId, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("scope_mismatch"));
  }

  @Test
  void createPosition_nonProfitEligibleUnit_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    Squadron squadron = squadron(unitId, "IRIDIUM", "IRI");
    squadron.setProfitEligible(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "lead")));
    when(orgUnitRepository.findById(unitId)).thenReturn(Optional.of(squadron));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SQUADRON_LEAD, unitId, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("unit_not_profit_eligible"));
  }

  @Test
  void createPosition_fifthCommandLead_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "cl")));
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(squadron(unitId, "IRIDIUM", "IRI")));
    when(positionRepository.countByOrgUnitIdAndPositionType(
            unitId, OrgChartPositionType.COMMAND_LEAD))
        .thenReturn(4L);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.COMMAND_LEAD, unitId, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("command_limit"));
  }

  @Test
  void createPosition_fifthEnsign_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "e")));
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(squadron(unitId, "IRIDIUM", "IRI")));
    when(positionRepository.countByOrgUnitIdAndPositionType(unitId, OrgChartPositionType.ENSIGN))
        .thenReturn(4L);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.ENSIGN, unitId, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("ensign_limit"));
  }

  @Test
  void createPosition_thirdSkCommander_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "c")));
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(specialCommand(unitId, "Alpha SK", "ASK")));
    when(positionRepository.countByOrgUnitIdAndPositionType(
            unitId, OrgChartPositionType.SK_COMMANDER))
        .thenReturn(2L);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.SK_COMMANDER, unitId, userId, null, null, null)));

    assertTrue(ex.getMessage().contains("commander_limit"));
  }

  @Test
  void createPosition_deputyWithoutParent_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "d")));
    when(orgUnitRepository.findById(unitId))
        .thenReturn(Optional.of(squadron(unitId, "IRIDIUM", "IRI")));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.DEPUTY_COMMAND_LEAD,
                            unitId,
                            userId,
                            null,
                            null,
                            null)));

    assertTrue(ex.getMessage().contains("invalid_parent"));
  }

  @Test
  void createPosition_ensignWithNonCommandParent_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    Squadron squadron = squadron(unitId, "IRIDIUM", "IRI");
    OrgChartPosition squadronLead = pos(OrgChartPositionType.SQUADRON_LEAD, squadron, null);
    squadronLead.setId(parentId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "e")));
    when(orgUnitRepository.findById(unitId)).thenReturn(Optional.of(squadron));
    when(positionRepository.findById(parentId)).thenReturn(Optional.of(squadronLead));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.ENSIGN, unitId, userId, parentId, null, null)));

    assertTrue(ex.getMessage().contains("invalid_parent"));
  }

  @Test
  void createPosition_secondDeputyForSameCommand_isRejected() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    UUID parentId = UUID.randomUUID();
    Squadron squadron = squadron(unitId, "IRIDIUM", "IRI");
    OrgChartPosition commandLead = pos(OrgChartPositionType.COMMAND_LEAD, squadron, null);
    commandLead.setId(parentId);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "d")));
    when(orgUnitRepository.findById(unitId)).thenReturn(Optional.of(squadron));
    when(positionRepository.findById(parentId)).thenReturn(Optional.of(commandLead));
    when(positionRepository.existsByParentIdAndPositionType(
            parentId, OrgChartPositionType.DEPUTY_COMMAND_LEAD))
        .thenReturn(true);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.DEPUTY_COMMAND_LEAD,
                            unitId,
                            userId,
                            parentId,
                            null,
                            null)));

    assertTrue(ex.getMessage().contains("duplicate_deputy"));
  }

  @Test
  void createPosition_userAlreadyInScope_isRejected() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "c")));
    when(positionRepository.existsByOrgUnitIsNullAndUserId(userId)).thenReturn(true);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .createPosition(
                        new OrgChartPositionCreateRequest(
                            OrgChartPositionType.AREA_COORDINATOR,
                            null,
                            userId,
                            null,
                            null,
                            null)));

    assertTrue(ex.getMessage().contains("user_already_assigned"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void createPosition_unknownUser_throwsNotFound() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () ->
            service()
                .createPosition(
                    new OrgChartPositionCreateRequest(
                        OrgChartPositionType.AREA_COORDINATOR, null, userId, null, null, null)));
  }

  @Test
  void createPosition_unknownOrgUnit_throwsNotFound() {
    UUID userId = UUID.randomUUID();
    UUID unitId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, "l")));
    when(orgUnitRepository.findById(unitId)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () ->
            service()
                .createPosition(
                    new OrgChartPositionCreateRequest(
                        OrgChartPositionType.SQUADRON_LEAD, unitId, userId, null, null, null)));
  }

  // ----------------------------------------------------------------- update / delete --

  @Test
  void updatePosition_staleVersion_throwsOptimisticLock() {
    UUID id = UUID.randomUUID();
    OrgChartPosition position = pos(OrgChartPositionType.AREA_COORDINATOR, null, null);
    position.setId(id);
    position.setVersion(3L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));

    assertThrows(
        ObjectOptimisticLockingFailureException.class,
        () ->
            service().updatePosition(id, new OrgChartPositionUpdateRequest(null, null, null, 1L)));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void updatePosition_reassignToUserAlreadyInScope_isRejected() {
    UUID id = UUID.randomUUID();
    UUID newUserId = UUID.randomUUID();
    OrgChartPosition position =
        pos(OrgChartPositionType.AREA_COORDINATOR, null, null, user(UUID.randomUUID(), "old"));
    position.setId(id);
    position.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));
    when(userRepository.findById(newUserId)).thenReturn(Optional.of(user(newUserId, "new")));
    when(positionRepository.existsByOrgUnitIsNullAndUserId(newUserId)).thenReturn(true);

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .updatePosition(
                        id, new OrgChartPositionUpdateRequest(newUserId, null, null, 0L)));

    assertTrue(ex.getMessage().contains("user_already_assigned"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void updatePosition_reassignToFreeUser_persists() {
    UUID id = UUID.randomUUID();
    UUID newUserId = UUID.randomUUID();
    OrgChartPosition position =
        pos(OrgChartPositionType.AREA_COORDINATOR, null, null, user(UUID.randomUUID(), "old"));
    position.setId(id);
    position.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));
    when(userRepository.findById(newUserId)).thenReturn(Optional.of(user(newUserId, "new")));
    when(positionRepository.existsByOrgUnitIsNullAndUserId(newUserId)).thenReturn(false);
    when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrgChartPositionDto dto =
        service().updatePosition(id, new OrgChartPositionUpdateRequest(newUserId, null, null, 0L));

    assertEquals(newUserId, dto.userId());
  }

  @Test
  void updatePosition_assignLeaderToLeaderlessKommando_persists() {
    UUID id = UUID.randomUUID();
    UUID newUserId = UUID.randomUUID();
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    OrgChartPosition kommando = pos(OrgChartPositionType.COMMAND_LEAD, squadron, null, null);
    kommando.setId(id);
    kommando.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(kommando));
    when(userRepository.findById(newUserId)).thenReturn(Optional.of(user(newUserId, "lead")));
    when(positionRepository.existsByOrgUnitIdAndUserId(squadron.getId(), newUserId))
        .thenReturn(false);
    when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrgChartPositionDto dto =
        service().updatePosition(id, new OrgChartPositionUpdateRequest(newUserId, null, null, 0L));

    assertEquals(newUserId, dto.userId());
  }

  @Test
  void updatePosition_renameCommand_persists() {
    UUID id = UUID.randomUUID();
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    OrgChartPosition kommando = pos(OrgChartPositionType.COMMAND_LEAD, squadron, null);
    kommando.setId(id);
    kommando.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(kommando));
    when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrgChartPositionDto dto =
        service().updatePosition(id, new OrgChartPositionUpdateRequest(null, "Bravo", null, 0L));

    assertEquals("Bravo", dto.name());
  }

  @Test
  void updatePosition_nameOnNonCommandRank_isRejected() {
    UUID id = UUID.randomUUID();
    OrgChartPosition position = pos(OrgChartPositionType.AREA_COORDINATOR, null, null);
    position.setId(id);
    position.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));

    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () ->
                service()
                    .updatePosition(id, new OrgChartPositionUpdateRequest(null, "Nope", null, 0L)));

    assertTrue(ex.getMessage().contains("name_not_allowed"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void updatePosition_unknownId_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(positionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(
        NotFoundException.class,
        () ->
            service().updatePosition(id, new OrgChartPositionUpdateRequest(null, null, null, 0L)));
  }

  @Test
  void deletePosition_present_deletes() {
    UUID id = UUID.randomUUID();
    OrgChartPosition position = pos(OrgChartPositionType.COMMAND_LEAD, null, null);
    position.setId(id);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));

    service().deletePosition(id);

    verify(positionRepository).delete(position);
  }

  @Test
  void deletePosition_unknownId_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(positionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service().deletePosition(id));
    verify(positionRepository, never()).delete(any());
  }

  // ----------------------------------------------------------------- vacate leader --

  @Test
  void vacateCommandLeader_clearsHolderButKeepsKommando() {
    UUID id = UUID.randomUUID();
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    OrgChartPosition kommando =
        pos(OrgChartPositionType.COMMAND_LEAD, squadron, null, user(UUID.randomUUID(), "lead"));
    kommando.setId(id);
    kommando.setVersion(2L);
    kommando.setName("Alpha");
    when(positionRepository.findById(id)).thenReturn(Optional.of(kommando));
    when(positionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OrgChartPositionDto dto = service().vacateCommandLeader(id, 2L);

    assertNull(dto.userId(), "the Kommandoleiter seat is now vacant");
    assertEquals("Alpha", dto.name(), "the Kommandogruppe itself stays — name preserved");
    assertNull(kommando.getUser(), "the holder is cleared on the managed entity");
    verify(positionRepository).save(kommando);
  }

  @Test
  void vacateCommandLeader_nonCommandRank_isRejected() {
    UUID id = UUID.randomUUID();
    OrgChartPosition position =
        pos(OrgChartPositionType.AREA_COORDINATOR, null, null, user(UUID.randomUUID(), "c"));
    position.setId(id);
    position.setVersion(0L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(position));

    BadRequestException ex =
        assertThrows(BadRequestException.class, () -> service().vacateCommandLeader(id, 0L));

    assertTrue(ex.getMessage().contains("vacate_not_command"));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void vacateCommandLeader_staleVersion_throwsOptimisticLock() {
    UUID id = UUID.randomUUID();
    Squadron squadron = squadron(UUID.randomUUID(), "IRIDIUM", "IRI");
    OrgChartPosition kommando =
        pos(OrgChartPositionType.COMMAND_LEAD, squadron, null, user(UUID.randomUUID(), "lead"));
    kommando.setId(id);
    kommando.setVersion(3L);
    when(positionRepository.findById(id)).thenReturn(Optional.of(kommando));

    assertThrows(
        ObjectOptimisticLockingFailureException.class, () -> service().vacateCommandLeader(id, 1L));
    verify(positionRepository, never()).save(any());
  }

  @Test
  void vacateCommandLeader_unknownId_throwsNotFound() {
    UUID id = UUID.randomUUID();
    when(positionRepository.findById(id)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> service().vacateCommandLeader(id, 0L));
    verify(positionRepository, never()).save(any());
  }

  // --------------------------------------------------------------------- fixtures --

  private static User user(UUID id, String username) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private static Squadron squadron(UUID id, String name, String shorthand) {
    Squadron squadron = new Squadron();
    squadron.setId(id);
    squadron.setName(name);
    squadron.setShorthand(shorthand);
    squadron.setActive(true);
    squadron.setProfitEligible(true);
    return squadron;
  }

  private static SpecialCommand specialCommand(UUID id, String name, String shorthand) {
    SpecialCommand sk = new SpecialCommand();
    sk.setId(id);
    sk.setName(name);
    sk.setShorthand(shorthand);
    sk.setActive(true);
    sk.setProfitEligible(true);
    return sk;
  }

  private static OrgChartPosition pos(
      OrgChartPositionType type, OrgUnit unit, OrgChartPosition parent) {
    return pos(type, unit, parent, user(UUID.randomUUID(), "u-" + type.name()));
  }

  private static OrgChartPosition pos(
      OrgChartPositionType type, OrgUnit unit, OrgChartPosition parent, User user) {
    OrgChartPosition position = new OrgChartPosition();
    position.setId(UUID.randomUUID());
    position.setPositionType(type);
    position.setOrgUnit(unit);
    position.setParent(parent);
    position.setUser(user);
    return position;
  }
}
