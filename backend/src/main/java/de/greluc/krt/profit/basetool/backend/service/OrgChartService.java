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

package de.greluc.krt.profit.basetool.backend.service;

import de.greluc.krt.profit.basetool.backend.exception.BadRequestException;
import de.greluc.krt.profit.basetool.backend.exception.NotFoundException;
import de.greluc.krt.profit.basetool.backend.mapper.OrgChartPositionMapper;
import de.greluc.krt.profit.basetool.backend.model.OrgChartPosition;
import de.greluc.krt.profit.basetool.backend.model.OrgChartPositionType;
import de.greluc.krt.profit.basetool.backend.model.OrgChartScope;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.User;
import de.greluc.krt.profit.basetool.backend.model.dto.AreaLeadershipDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CommandChartDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgChartDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgChartNodeDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgChartPositionCreateRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgChartPositionDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgChartPositionUpdateRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.SpecialCommandChartDto;
import de.greluc.krt.profit.basetool.backend.model.dto.SquadronChartDto;
import de.greluc.krt.profit.basetool.backend.repository.OrgChartPositionRepository;
import de.greluc.krt.profit.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.profit.basetool.backend.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles and mutates the Profit-Bereich org chart ({@link OrgChartPosition} aggregate). The
 * chart is purely descriptive — placing a user in a position grants no permission — so this service
 * is NOT org-unit-scoped: it deliberately does not inject {@code OwnerScopeService} / {@code
 * AuthHelperService} and must stay off the {@code
 * staffelScopedServicesMustWireOwnerScopeOrAuthHelper} ArchUnit whitelist. Read access is open to
 * every authenticated user; write access is gated to ADMIN at the controller.
 *
 * <p>The unit tier is exactly the active, profit-eligible Staffeln + SKs (loaded via {@link
 * OrgUnitRepository#findActiveProfitEligible()}); every such unit is rendered even when empty so an
 * admin can fill it in.
 *
 * <p>All structural invariants the database cannot express in plain SQL — the per-Staffel limits
 * (≤{@value #MAX_COMMAND_LEADS} Kommandos, ≤{@value #MAX_ENSIGNS} Ensign), the per-SK limit
 * (≤{@value #MAX_SK_COMMANDERS} SK-Leiter), the parent/scope consistency rules, the "name /
 * nullable holder only on a Kommando" rule, and "a user appears at most once per scope" — are
 * enforced here and surface as 400 problem responses. The singleton rules (one Bereichsleiter, one
 * Staffelleiter per Staffel, one Stv. per Kommando, one position per user and scope) are
 * additionally backstopped by partial unique indexes in migrations {@code V136}/{@code V138}, so a
 * concurrent double-create there fails cleanly as a 409. The count caps (≤4/≤2) are service-layer
 * only: two interleaved creates could momentarily exceed a cap by one — harmless for a descriptive,
 * ADMIN-only chart where an admin simply removes the surplus.
 *
 * <p>A {@link OrgChartPositionType#COMMAND_LEAD} row models the Kommando(gruppe) itself, carrying
 * an optional {@code name} and an optional holder (the Kommandoleiter). This lets an admin create
 * and name a Kommando, hang a Stv. Kommandoleiter and Ensigns off it, and only later assign its
 * Kommandoleiter — so a subordinate seat is fillable while its superior is still vacant.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgChartService {

  /** Maximum number of Kommandos (COMMAND_LEAD rows) per Staffel. */
  static final int MAX_COMMAND_LEADS = 4;

  /**
   * Maximum number of Ensign positions per Staffel (directly-attached plus all under Kommandos).
   */
  static final int MAX_ENSIGNS = 4;

  /** Maximum number of SK-Leiter (SK_COMMANDER) positions per Spezialkommando. */
  static final int MAX_SK_COMMANDERS = 2;

  private static final String ERR_SCOPE_MISMATCH = "problem.org_chart.scope_mismatch";
  private static final String ERR_UNIT_NOT_PROFIT = "problem.org_chart.unit_not_profit_eligible";
  private static final String ERR_INVALID_PARENT = "problem.org_chart.invalid_parent";
  private static final String ERR_COMMAND_LIMIT = "problem.org_chart.command_limit";
  private static final String ERR_ENSIGN_LIMIT = "problem.org_chart.ensign_limit";
  private static final String ERR_COMMANDER_LIMIT = "problem.org_chart.commander_limit";
  private static final String ERR_DUPLICATE_LEAD = "problem.org_chart.duplicate_lead";
  private static final String ERR_DUPLICATE_DEPUTY = "problem.org_chart.duplicate_deputy";
  private static final String ERR_USER_ASSIGNED = "problem.org_chart.user_already_assigned";
  private static final String ERR_USER_REQUIRED = "problem.org_chart.user_required";
  private static final String ERR_NAME_NOT_ALLOWED = "problem.org_chart.name_not_allowed";
  private static final String ERR_VACATE_NOT_COMMAND = "problem.org_chart.vacate_not_command";

  private final OrgChartPositionRepository positionRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final UserRepository userRepository;
  private final OrgChartPositionMapper mapper;

  /**
   * Assembles the entire chart as one nested read model: the Bereichsleitung plus a column for each
   * active, profit-eligible Staffel and Spezialkommando, ordered by name. Open to every
   * authenticated user.
   *
   * @return the assembled chart; never {@code null}. Empty scopes render as empty groups.
   */
  public OrgChartDto getOrgChart() {
    List<OrgUnit> units = orgUnitRepository.findActiveProfitEligible();
    List<OrgUnit> squadrons =
        units.stream()
            .filter(u -> u.getKind() == OrgUnitKind.SQUADRON)
            .sorted(Comparator.comparing(OrgUnit::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    List<OrgUnit> specialCommands =
        units.stream()
            .filter(u -> u.getKind() == OrgUnitKind.SPECIAL_COMMAND)
            .sorted(Comparator.comparing(OrgUnit::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();

    List<OrgChartPosition> areaPositions =
        positionRepository.findAllByOrgUnitIsNullOrderBySortIndexAscCreatedAtAsc();

    Set<UUID> unitIds = units.stream().map(OrgUnit::getId).collect(Collectors.toSet());
    List<OrgChartPosition> unitPositions =
        unitIds.isEmpty()
            ? List.of()
            : positionRepository.findAllByOrgUnitIdInOrderBySortIndexAscCreatedAtAsc(unitIds);
    Map<UUID, List<OrgChartPosition>> positionsByUnit =
        unitPositions.stream().collect(Collectors.groupingBy(p -> p.getOrgUnit().getId()));

    return new OrgChartDto(
        buildAreaLeadership(areaPositions),
        squadrons.stream()
            .map(s -> buildSquadron(s, positionsByUnit.getOrDefault(s.getId(), List.of())))
            .toList(),
        specialCommands.stream()
            .map(sk -> buildSpecialCommand(sk, positionsByUnit.getOrDefault(sk.getId(), List.of())))
            .toList());
  }

  /**
   * Creates a new position, validating scope/type consistency, parent rules, cardinality limits,
   * the name/holder rules and the one-user-per-scope rule before persisting. ADMIN-only at the
   * controller. A {@code COMMAND_LEAD} create may omit the holder to make a still-leaderless
   * Kommando and may carry a {@code name}; every other rank requires a holder and rejects a name.
   *
   * @param request the assignment payload; never {@code null}.
   * @return the persisted position as a flat DTO with id + version populated.
   * @throws NotFoundException if the user, the OrgUnit, or the referenced parent does not exist.
   * @throws BadRequestException if any scope/parent/cardinality/name/uniqueness rule is violated.
   */
  @Transactional
  public OrgChartPositionDto createPosition(@NotNull OrgChartPositionCreateRequest request) {
    OrgChartPositionType type = request.positionType();
    OrgChartScope scope = type.scope();
    final User user = resolveHolderForCreate(type, request.userId());

    OrgUnit orgUnit = resolveScopeOrgUnit(scope, request.orgUnitId());
    final OrgChartPosition parent = resolveAndValidateParent(type, orgUnit, request.parentId());
    validateCardinality(type, orgUnit);
    final String name = validateAndNormalizeName(type, request.name());
    if (user != null) {
      validateUserUnique(scope, orgUnit, request.userId());
    }

    OrgChartPosition position = new OrgChartPosition();
    position.setPositionType(type);
    position.setOrgUnit(orgUnit);
    position.setUser(user);
    position.setName(name);
    position.setParent(parent);
    position.setSortIndex(request.sortIndex() != null ? request.sortIndex() : 0);
    return mapper.toDto(positionRepository.save(position));
  }

  /**
   * Reassigns the holder, renames a Kommando and/or reorders an existing position. The functional
   * rank, scope and parent are immutable after creation (move = remove + re-add), so only {@code
   * userId}, {@code name} (Kommando only) and {@code sortIndex} are honoured; a {@code null} field
   * leaves the current value unchanged, while a blank {@code name} clears it. Assigning a holder to
   * a still-leaderless Kommando is just a reassign through {@code userId}.
   *
   * @param id the position id; never {@code null}.
   * @param request the edit payload carrying the current optimistic-lock version; never {@code
   *     null}.
   * @return the updated position as a flat DTO with the bumped version.
   * @throws NotFoundException if the position or the new user does not exist.
   * @throws BadRequestException if the new holder already occupies a position in the same scope, or
   *     a name is supplied for a non-Kommando rank.
   * @throws ObjectOptimisticLockingFailureException if the supplied version is stale.
   */
  @Transactional
  public OrgChartPositionDto updatePosition(
      @NotNull UUID id, @NotNull OrgChartPositionUpdateRequest request) {
    OrgChartPosition position =
        positionRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("OrgChartPosition not found: " + id));
    if (position.getVersion() != null && !position.getVersion().equals(request.version())) {
      throw new ObjectOptimisticLockingFailureException(OrgChartPosition.class, id);
    }
    if (request.name() != null) {
      if (position.getPositionType() != OrgChartPositionType.COMMAND_LEAD) {
        throw new BadRequestException(ERR_NAME_NOT_ALLOWED);
      }
      position.setName(trimToNull(request.name()));
    }
    if (request.userId() != null) {
      User current = position.getUser();
      if (current == null || !request.userId().equals(current.getId())) {
        User newUser =
            userRepository
                .findById(request.userId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.userId()));
        validateUserUnique(
            position.getPositionType().scope(), position.getOrgUnit(), request.userId());
        position.setUser(newUser);
      }
    }
    if (request.sortIndex() != null) {
      position.setSortIndex(request.sortIndex());
    }
    return mapper.toDto(positionRepository.save(position));
  }

  /**
   * Vacates the Kommandoleiter seat of a Kommando(gruppe): clears the holder on a {@code
   * COMMAND_LEAD} row while leaving the row itself — its name, its Stv. Kommandoleiter and its
   * Ensigns — intact. This is the inverse of assigning a leader through {@link #updatePosition} and
   * the reason a Kommando outlives a departing Kommandoleiter instead of having to be deleted and
   * rebuilt. Only a {@code COMMAND_LEAD} carries a nullable holder (the {@code chk_org_chart_user}
   * CHECK keeps every other rank's holder mandatory), so every other rank is rejected as a 400:
   * removing such a person-centric position is {@link #deletePosition} instead. ADMIN-only at the
   * controller.
   *
   * @param id the Kommando position id; never {@code null}.
   * @param version the optimistic-lock version the client last saw; a mismatch surfaces as 409.
   * @return the updated, now-leaderless Kommando as a flat DTO with the bumped version.
   * @throws NotFoundException if no position matches the id.
   * @throws BadRequestException if the position is not a {@code COMMAND_LEAD} Kommando.
   * @throws ObjectOptimisticLockingFailureException if the supplied version is stale.
   */
  @Transactional
  public OrgChartPositionDto vacateCommandLeader(@NotNull UUID id, long version) {
    OrgChartPosition position =
        positionRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("OrgChartPosition not found: " + id));
    if (position.getPositionType() != OrgChartPositionType.COMMAND_LEAD) {
      throw new BadRequestException(ERR_VACATE_NOT_COMMAND);
    }
    if (position.getVersion() != null && !position.getVersion().equals(version)) {
      throw new ObjectOptimisticLockingFailureException(OrgChartPosition.class, id);
    }
    position.setUser(null);
    return mapper.toDto(positionRepository.save(position));
  }

  /**
   * Removes a position. Removing a Kommando (COMMAND_LEAD) cascades to its Stv. Kommandoleiter and
   * the Ensigns reporting into it (the {@code parent_id} FK is {@code ON DELETE CASCADE}); the
   * inline editor warns the admin of the affected children before calling this. ADMIN-only at the
   * controller.
   *
   * @param id the position id; never {@code null}.
   * @throws NotFoundException if no position matches the id.
   */
  @Transactional
  public void deletePosition(@NotNull UUID id) {
    OrgChartPosition position =
        positionRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("OrgChartPosition not found: " + id));
    positionRepository.delete(position);
  }

  // ---------------------------------------------------------------- read assembly --

  private AreaLeadershipDto buildAreaLeadership(List<OrgChartPosition> positions) {
    OrgChartNodeDto lead =
        positions.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.AREA_LEAD)
            .findFirst()
            .map(mapper::toNode)
            .orElse(null);
    return new AreaLeadershipDto(
        lead,
        nodesOfType(positions, OrgChartPositionType.AREA_COMMANDER),
        nodesOfType(positions, OrgChartPositionType.AREA_COORDINATOR),
        nodesOfType(positions, OrgChartPositionType.AREA_OPERATOR));
  }

  private SquadronChartDto buildSquadron(OrgUnit unit, List<OrgChartPosition> positions) {
    OrgChartNodeDto lead =
        positions.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.SQUADRON_LEAD)
            .findFirst()
            .map(mapper::toNode)
            .orElse(null);
    List<OrgChartPosition> commands =
        positions.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.COMMAND_LEAD)
            .toList();
    List<CommandChartDto> commandDtos =
        commands.stream().map(cmd -> buildCommand(cmd, positions)).toList();
    List<OrgChartNodeDto> directEnsigns =
        positions.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.ENSIGN)
            .filter(p -> p.getParent() == null)
            .map(mapper::toNode)
            .toList();
    long ensignCount =
        positions.stream().filter(p -> p.getPositionType() == OrgChartPositionType.ENSIGN).count();
    return new SquadronChartDto(
        unit.getId(),
        unit.getName(),
        unit.getShorthand(),
        lead,
        commandDtos,
        directEnsigns,
        commands.size() < MAX_COMMAND_LEADS,
        ensignCount < MAX_ENSIGNS);
  }

  /**
   * Projects one Kommando row plus its children into a {@link CommandChartDto}. The Kommandoleiter
   * lives on the Kommando row itself, so it is carried inline ({@code null} while vacant); the Stv.
   * and Ensigns are the rows whose {@code parent_id} points back at this Kommando.
   *
   * @param command the Kommando ({@code COMMAND_LEAD}) row, with its user fetched.
   * @param siblings every position of the owning Staffel, used to find this Kommando's children.
   * @return the assembled Kommando DTO; never {@code null}.
   */
  private CommandChartDto buildCommand(OrgChartPosition command, List<OrgChartPosition> siblings) {
    User leader = command.getUser();
    OrgChartNodeDto deputy =
        siblings.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.DEPUTY_COMMAND_LEAD)
            .filter(p -> isChildOf(p, command))
            .findFirst()
            .map(mapper::toNode)
            .orElse(null);
    List<OrgChartNodeDto> ensigns =
        siblings.stream()
            .filter(p -> p.getPositionType() == OrgChartPositionType.ENSIGN)
            .filter(p -> isChildOf(p, command))
            .map(mapper::toNode)
            .toList();
    return new CommandChartDto(
        command.getId(),
        command.getName(),
        command.getVersion(),
        command.getSortIndex(),
        leader != null ? leader.getId() : null,
        leader != null ? leader.getEffectiveName() : null,
        deputy,
        ensigns);
  }

  private SpecialCommandChartDto buildSpecialCommand(
      OrgUnit unit, List<OrgChartPosition> positions) {
    List<OrgChartNodeDto> commanders = nodesOfType(positions, OrgChartPositionType.SK_COMMANDER);
    return new SpecialCommandChartDto(
        unit.getId(),
        unit.getName(),
        unit.getShorthand(),
        commanders,
        commanders.size() < MAX_SK_COMMANDERS);
  }

  private List<OrgChartNodeDto> nodesOfType(
      List<OrgChartPosition> positions, OrgChartPositionType type) {
    return positions.stream().filter(p -> p.getPositionType() == type).map(mapper::toNode).toList();
  }

  private static boolean isChildOf(OrgChartPosition child, OrgChartPosition parent) {
    return child.getParent() != null && parent.getId().equals(child.getParent().getId());
  }

  // ----------------------------------------------------------------- write guards --

  private User resolveHolderForCreate(OrgChartPositionType type, UUID userId) {
    if (userId == null) {
      if (type == OrgChartPositionType.COMMAND_LEAD) {
        return null; // a Kommando may be created before its Kommandoleiter is assigned
      }
      throw new BadRequestException(ERR_USER_REQUIRED);
    }
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
  }

  private String validateAndNormalizeName(OrgChartPositionType type, String rawName) {
    String normalized = trimToNull(rawName);
    if (normalized != null && type != OrgChartPositionType.COMMAND_LEAD) {
      throw new BadRequestException(ERR_NAME_NOT_ALLOWED);
    }
    return normalized;
  }

  private OrgUnit resolveScopeOrgUnit(OrgChartScope scope, UUID orgUnitId) {
    if (scope == OrgChartScope.AREA) {
      if (orgUnitId != null) {
        throw new BadRequestException(ERR_SCOPE_MISMATCH);
      }
      return null;
    }
    if (orgUnitId == null) {
      throw new BadRequestException(ERR_SCOPE_MISMATCH);
    }
    OrgUnit unit =
        orgUnitRepository
            .findById(orgUnitId)
            .orElseThrow(() -> new NotFoundException("OrgUnit not found: " + orgUnitId));
    OrgUnitKind expectedKind =
        switch (scope) {
          case SQUADRON -> OrgUnitKind.SQUADRON;
          case SPECIAL_COMMAND -> OrgUnitKind.SPECIAL_COMMAND;
          case BEREICH -> OrgUnitKind.BEREICH;
          case OL -> OrgUnitKind.ORGANISATIONSLEITUNG;
          case AREA -> throw new IllegalStateException("AREA scope handled above");
        };
    if (unit.getKind() != expectedKind) {
      throw new BadRequestException(ERR_SCOPE_MISMATCH);
    }
    // Profit-eligibility is required only for the Job-Order-processing tiers (Staffel/SK) — those
    // are the org chart's historical "Profit-Bereich" units. A Bereich/OL (epic #692, REQ-ORG-018)
    // is never profit-eligible and must not be rejected for it; only its active flag is checked.
    if (scope == OrgChartScope.SQUADRON || scope == OrgChartScope.SPECIAL_COMMAND) {
      if (!unit.isActive() || !unit.isProfitEligible()) {
        throw new BadRequestException(ERR_UNIT_NOT_PROFIT);
      }
    } else if (!unit.isActive()) {
      throw new BadRequestException(ERR_UNIT_NOT_PROFIT);
    }
    return unit;
  }

  private OrgChartPosition resolveAndValidateParent(
      OrgChartPositionType type, OrgUnit orgUnit, UUID parentId) {
    if (type != OrgChartPositionType.DEPUTY_COMMAND_LEAD && type != OrgChartPositionType.ENSIGN) {
      if (parentId != null) {
        throw new BadRequestException(ERR_INVALID_PARENT);
      }
      return null;
    }
    if (type == OrgChartPositionType.DEPUTY_COMMAND_LEAD) {
      if (parentId == null) {
        throw new BadRequestException(ERR_INVALID_PARENT);
      }
      OrgChartPosition parent = loadCommandLeadParent(parentId, orgUnit);
      if (positionRepository.existsByParentIdAndPositionType(
          parentId, OrgChartPositionType.DEPUTY_COMMAND_LEAD)) {
        throw new BadRequestException(ERR_DUPLICATE_DEPUTY);
      }
      return parent;
    }
    // ENSIGN: a null parent means "reports directly to the Staffelleiter"; a non-null parent must
    // be a Kommando in the same Staffel.
    return parentId == null ? null : loadCommandLeadParent(parentId, orgUnit);
  }

  private OrgChartPosition loadCommandLeadParent(UUID parentId, OrgUnit orgUnit) {
    OrgChartPosition parent =
        positionRepository
            .findById(parentId)
            .orElseThrow(() -> new NotFoundException("Parent position not found: " + parentId));
    if (parent.getPositionType() != OrgChartPositionType.COMMAND_LEAD
        || parent.getOrgUnit() == null
        || !parent.getOrgUnit().getId().equals(orgUnit.getId())) {
      throw new BadRequestException(ERR_INVALID_PARENT);
    }
    return parent;
  }

  private void validateCardinality(OrgChartPositionType type, OrgUnit orgUnit) {
    switch (type) {
      case AREA_LEAD -> {
        if (positionRepository.existsByOrgUnitIsNullAndPositionType(
            OrgChartPositionType.AREA_LEAD)) {
          throw new BadRequestException(ERR_DUPLICATE_LEAD);
        }
      }
      case SQUADRON_LEAD -> {
        if (positionRepository.countByOrgUnitIdAndPositionType(
                orgUnit.getId(), OrgChartPositionType.SQUADRON_LEAD)
            > 0) {
          throw new BadRequestException(ERR_DUPLICATE_LEAD);
        }
      }
      case COMMAND_LEAD -> {
        if (positionRepository.countByOrgUnitIdAndPositionType(
                orgUnit.getId(), OrgChartPositionType.COMMAND_LEAD)
            >= MAX_COMMAND_LEADS) {
          throw new BadRequestException(ERR_COMMAND_LIMIT);
        }
      }
      case ENSIGN -> {
        if (positionRepository.countByOrgUnitIdAndPositionType(
                orgUnit.getId(), OrgChartPositionType.ENSIGN)
            >= MAX_ENSIGNS) {
          throw new BadRequestException(ERR_ENSIGN_LIMIT);
        }
      }
      case SK_COMMANDER -> {
        if (positionRepository.countByOrgUnitIdAndPositionType(
                orgUnit.getId(), OrgChartPositionType.SK_COMMANDER)
            >= MAX_SK_COMMANDERS) {
          throw new BadRequestException(ERR_COMMANDER_LIMIT);
        }
      }
      case BEREICHSLEITER -> {
        // Epic #692, REQ-ORG-018: at most one Bereichsleiter PER Bereich (scoped to org_unit_id),
        // unlike the legacy AREA_LEAD which is a global singleton.
        if (positionRepository.countByOrgUnitIdAndPositionType(
                orgUnit.getId(), OrgChartPositionType.BEREICHSLEITER)
            > 0) {
          throw new BadRequestException(ERR_DUPLICATE_LEAD);
        }
      }
      // DEPUTY_COMMAND_LEAD's "at most one per Kommando" is enforced during parent resolution;
      // AREA_COORDINATOR / AREA_OPERATOR / AREA_COMMANDER, BEREICHSKOORDINATOR / BEREICHSOPERATOR
      // and OL_MEMBER are unbounded.
      default -> {
        // no cardinality limit
      }
    }
  }

  private void validateUserUnique(OrgChartScope scope, OrgUnit orgUnit, UUID userId) {
    boolean alreadyAssigned =
        scope == OrgChartScope.AREA
            ? positionRepository.existsByOrgUnitIsNullAndUserId(userId)
            : positionRepository.existsByOrgUnitIdAndUserId(orgUnit.getId(), userId);
    if (alreadyAssigned) {
      throw new BadRequestException(ERR_USER_ASSIGNED);
    }
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
