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

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderBlueprintOwnerDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemBlueprintOwnersDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderRequiredBlueprintDto;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitMembershipRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the item job-order <em>blueprint-coverage</em> view: for an {@code ITEM} order, who
 * among the members of the order's responsible (processing) squadron/SK owns the blueprints for the
 * items the order requests, and which of those required blueprints each member holds.
 *
 * <p>{@link PersonalBlueprint} carries no org-unit column — it is a per-user aggregate keyed by the
 * Keycloak {@code sub}. This service bridges a job order's required items to org-unit members
 * entirely in Java, mirroring {@link PersonalBlueprintOverviewService}: it normalizes each item
 * line's chosen-blueprint output name into a {@code product_key} (the same identity {@link
 * BlueprintNameNormalizer} computes for personal-blueprint ownership), resolves the responsible org
 * unit's member ids to their {@code sub} form (the {@code owner_sub} stored on {@link
 * PersonalBlueprint} equals {@code User.id}), and groups the matching owned-blueprint rows by owner
 * and by product.
 *
 * <p>Member identity never leaves the service except as a display name — owners are exposed only
 * via {@link User#getEffectiveName()} (never the {@code sub} or e-mail), preserving the
 * data-isolation rule. The endpoint above this service is gated members-only ({@code
 * @ownerScopeService.canSeeJobOrderBlueprintOwners}), so a non-member viewing an otherwise-public
 * SK order never reaches this aggregation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobOrderItemBlueprintOwnersService {

  private final JobOrderRepository jobOrderRepository;
  private final OrgUnitMembershipRepository orgUnitMembershipRepository;
  private final PersonalBlueprintRepository personalBlueprintRepository;
  private final UserRepository userRepository;
  private final BlueprintNameNormalizer normalizer;

  /**
   * Builds the blueprint-coverage view for the given order. {@code MATERIAL} orders (and item
   * orders whose lines resolve to no blueprint product) yield an empty view. The owner grouping is
   * restricted to the order's responsible org unit's members and to the order's required products,
   * so neither foreign members nor a member's unrelated blueprints are exposed.
   *
   * @param jobOrderId the job order to inspect; never {@code null}
   * @return the coverage view (required products with owner counts + owning members with their
   *     owned required products); never {@code null}
   * @throws EntityNotFoundException when the order id is unknown
   */
  @NotNull
  public JobOrderItemBlueprintOwnersDto getBlueprintOwners(@NotNull UUID jobOrderId) {
    JobOrder order =
        jobOrderRepository
            .findByIdWithItemBlueprints(jobOrderId)
            .orElseThrow(() -> new EntityNotFoundException("Job order not found: " + jobOrderId));

    // Required products: normalized product key -> first-seen display name. Item lines whose
    // blueprint output name normalizes to nothing are skipped; a MATERIAL order has no item lines.
    Map<String, String> requiredByKey = new LinkedHashMap<>();
    for (JobOrderItem item : order.getItems()) {
      String outputName = item.getBlueprint() == null ? null : item.getBlueprint().getOutputName();
      String key = normalizer.normalize(outputName);
      if (key.isEmpty()) {
        continue;
      }
      String displayName = item.getGameItem() != null ? item.getGameItem().getName() : outputName;
      requiredByKey.putIfAbsent(key, displayName);
    }
    if (requiredByKey.isEmpty()) {
      return new JobOrderItemBlueprintOwnersDto(List.of(), List.of());
    }

    // Owner subs of the responsible org unit's members. A null responsible (legacy pre-backfill
    // row) yields no members, so every required product shows zero coverage.
    OrgUnit responsible = order.getResponsibleOrgUnit();
    Set<String> ownerSubs =
        responsible == null
            ? Set.of()
            : orgUnitMembershipRepository
                .findDistinctUserIdsByOrgUnitIdIn(Set.of(responsible.getId()))
                .stream()
                .map(UUID::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));

    // Group the matching owned rows by owner id; the (owner_sub, product_key) unique constraint
    // guarantees at most one row per owner per product, so the per-owner set is the owner's owned
    // subset of the required products.
    Map<UUID, Set<String>> ownedKeysByOwnerId = new LinkedHashMap<>();
    if (!ownerSubs.isEmpty()) {
      for (PersonalBlueprint bp :
          personalBlueprintRepository.findAllByOwnerSubInAndProductKeyIn(
              ownerSubs, requiredByKey.keySet())) {
        UUID ownerId = parseUuid(bp.getOwnerSub());
        if (ownerId == null) {
          continue;
        }
        ownedKeysByOwnerId
            .computeIfAbsent(ownerId, id -> new LinkedHashSet<>())
            .add(bp.getProductKey());
      }
    }

    // Per-product distinct owner counts, derived from the per-owner owned sets.
    Map<String, Integer> ownerCountByKey = new HashMap<>();
    for (Set<String> ownedKeys : ownedKeysByOwnerId.values()) {
      for (String key : ownedKeys) {
        ownerCountByKey.merge(key, 1, Integer::sum);
      }
    }

    List<JobOrderRequiredBlueprintDto> requiredBlueprints =
        requiredByKey.entrySet().stream()
            .map(
                e ->
                    new JobOrderRequiredBlueprintDto(
                        e.getKey(), e.getValue(), ownerCountByKey.getOrDefault(e.getKey(), 0)))
            .sorted(
                Comparator.comparing(
                        JobOrderRequiredBlueprintDto::productName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(JobOrderRequiredBlueprintDto::productKey))
            .toList();

    List<JobOrderBlueprintOwnerDto> owners = buildOwners(ownedKeysByOwnerId, requiredByKey);
    return new JobOrderItemBlueprintOwnersDto(requiredBlueprints, owners);
  }

  /**
   * Resolves the grouped owner ids to display-name rows, mapping each owner's owned product keys
   * back to the order's display names. Owners whose id no longer resolves to a {@link User} are
   * dropped; the remaining rows are sorted by member name.
   *
   * @param ownedKeysByOwnerId owner id → the required product keys that owner holds
   * @param requiredByKey required product key → display name
   * @return the owning-member rows, sorted case-insensitively by name; never {@code null}
   */
  @NotNull
  private List<JobOrderBlueprintOwnerDto> buildOwners(
      @NotNull Map<UUID, Set<String>> ownedKeysByOwnerId,
      @NotNull Map<String, String> requiredByKey) {
    if (ownedKeysByOwnerId.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> nameById =
        userRepository.findAllById(ownedKeysByOwnerId.keySet()).stream()
            .collect(Collectors.toMap(User::getId, User::getEffectiveName));
    return ownedKeysByOwnerId.entrySet().stream()
        .filter(e -> nameById.containsKey(e.getKey()))
        .map(
            e ->
                new JobOrderBlueprintOwnerDto(
                    nameById.get(e.getKey()),
                    e.getValue().stream()
                        .map(requiredByKey::get)
                        .filter(Objects::nonNull)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList()))
        .sorted(
            Comparator.comparing(
                JobOrderBlueprintOwnerDto::ownerName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /**
   * Parses a stored {@code owner_sub} back into a {@link UUID}, returning {@code null} for the
   * (theoretical) malformed value so it is filtered out instead of aborting the aggregation.
   *
   * @param raw the {@code owner_sub} string
   * @return the parsed id, or {@code null} when {@code raw} is not a UUID
   */
  @Nullable
  private static UUID parseUuid(String raw) {
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
