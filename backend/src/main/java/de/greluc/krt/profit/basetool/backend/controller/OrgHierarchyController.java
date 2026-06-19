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

package de.greluc.krt.profit.basetool.backend.controller;

import de.greluc.krt.profit.basetool.backend.model.Bereich;
import de.greluc.krt.profit.basetool.backend.model.OrgUnit;
import de.greluc.krt.profit.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.profit.basetool.backend.model.Organisationsleitung;
import de.greluc.krt.profit.basetool.backend.model.dto.BereichDto;
import de.greluc.krt.profit.basetool.backend.model.dto.OrgUnitParentUpdateRequest;
import de.greluc.krt.profit.basetool.backend.model.dto.OrganisationsleitungDto;
import de.greluc.krt.profit.basetool.backend.service.OrgHierarchyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only REST surface for the upper org-hierarchy tiers (epic #692, REQ-ORG-014): creating
 * Bereiche and the Organisationsleitung and wiring the {@code parent_org_unit_id} edges that make a
 * Staffel/SK belong to a Bereich and a Bereich belong to the OL. Every endpoint is {@code
 * hasRole('ADMIN')} — defining the hierarchy is org-unit lifecycle administration, exactly like the
 * Squadron/SK lifecycle controllers. The leadership-membership management of Bereiche/OL lives on a
 * separate membership surface (a later slice of this phase).
 *
 * <p>Lives under {@code /api/v1/org-hierarchy/**}, outside the per-kind {@code /squadrons} / {@code
 * /special-commands} spaces, so the new tiers have one auditable home.
 */
@RestController
@RequestMapping("/api/v1/org-hierarchy")
@RequiredArgsConstructor
@Transactional
public class OrgHierarchyController {

  private final OrgHierarchyService orgHierarchyService;

  /**
   * Lists Bereiche for the admin overview.
   *
   * @param includeInactive include soft-deleted rows; defaults to {@code false}.
   * @return the Bereich DTOs.
   */
  @GetMapping("/bereiche")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "List Bereiche", description = "Admin-only list of the area (Bereich) tier.")
  public List<BereichDto> listBereiche(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    return orgHierarchyService.listBereiche(includeInactive).stream().map(this::toDto).toList();
  }

  /**
   * Creates a Bereich, optionally already wired under the Organisationsleitung.
   *
   * @param dto create payload; {@code id}/{@code version} are ignored (server-stamped).
   * @return the persisted Bereich DTO.
   */
  @PostMapping("/bereiche")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Create a Bereich",
      description = "Creates an area (Bereich); name/shorthand unique across all org units.")
  public BereichDto createBereich(@RequestBody @Valid BereichDto dto) {
    return toDto(
        orgHierarchyService.createBereich(
            dto.name(), dto.shorthand(), dto.description(), dto.parentOrgUnitId()));
  }

  /**
   * Lists the Organisationsleitung (normally exactly one row).
   *
   * @param includeInactive include a soft-deleted OL; defaults to {@code false}.
   * @return the OL DTO(s).
   */
  @GetMapping("/organisationsleitung")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "List the Organisationsleitung",
      description = "Admin-only list of the single top-of-hierarchy org unit.")
  public List<OrganisationsleitungDto> listOrganisationsleitung(
      @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
    return orgHierarchyService.listOrganisationsleitung(includeInactive).stream()
        .map(this::toDto)
        .toList();
  }

  /**
   * Creates the Organisationsleitung. Rejected with 409 if one already exists.
   *
   * @param dto create payload; {@code id}/{@code version} are ignored (server-stamped).
   * @return the persisted OL DTO.
   */
  @PostMapping("/organisationsleitung")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Create the Organisationsleitung",
      description = "Creates the single top-of-hierarchy org unit; a second one is rejected (409).")
  public OrganisationsleitungDto createOrganisationsleitung(
      @RequestBody @Valid OrganisationsleitungDto dto) {
    return toDto(
        orgHierarchyService.createOrganisationsleitung(
            dto.name(), dto.shorthand(), dto.description()));
  }

  /**
   * Sets (or clears) an org unit's parent in the hierarchy. The parent kind is validated against
   * the child's level (Staffel/SK → Bereich, Bereich → OL).
   *
   * @param id the child org unit id.
   * @param request the new parent id (or {@code null} to detach) plus the child's version.
   * @return the child's id, kind, new parent id and bumped version.
   */
  @PatchMapping("/org-units/{id}/parent")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Set an org unit's parent",
      description =
          "Assigns a Staffel/SK to a Bereich, or a Bereich to the Organisationsleitung. Validates"
              + " the kind pairing and the child's optimistic-lock version.")
  public OrgUnitParentResponse setParent(
      @PathVariable @NotNull UUID id, @RequestBody @Valid OrgUnitParentUpdateRequest request) {
    OrgUnit updated =
        orgHierarchyService.setParent(id, request.parentOrgUnitId(), request.version());
    return new OrgUnitParentResponse(
        updated.getId(),
        updated.getKind(),
        updated.getParent() == null ? null : updated.getParent().getId(),
        updated.getVersion());
  }

  /**
   * Maps a {@link Bereich} entity to its wire DTO. Reads the (lazy) parent id within the
   * controller's transaction; {@code getId()} on the proxy needs no DB hit.
   *
   * @param b the entity; never {@code null}.
   * @return the DTO.
   */
  private BereichDto toDto(@NotNull Bereich b) {
    return new BereichDto(
        b.getId(),
        b.getName(),
        b.getShorthand(),
        b.getDescription(),
        b.isActive(),
        b.getParent() == null ? null : b.getParent().getId(),
        b.getVersion());
  }

  /**
   * Maps an {@link Organisationsleitung} entity to its wire DTO.
   *
   * @param o the entity; never {@code null}.
   * @return the DTO.
   */
  private OrganisationsleitungDto toDto(@NotNull Organisationsleitung o) {
    return new OrganisationsleitungDto(
        o.getId(), o.getName(), o.getShorthand(), o.getDescription(), o.isActive(), o.getVersion());
  }

  /**
   * Response for the set-parent endpoint: the child's identity plus its freshly-bumped optimistic-
   * lock version so the caller can chain further edits.
   *
   * @param orgUnitId the child org unit id.
   * @param kind the child's kind.
   * @param parentOrgUnitId the new parent id, or {@code null} when detached.
   * @param version the bumped optimistic-lock version.
   */
  public record OrgUnitParentResponse(
      UUID orgUnitId, OrgUnitKind kind, UUID parentOrgUnitId, Long version) {}
}
