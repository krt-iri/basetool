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

import de.greluc.krt.profit.basetool.backend.model.Material;
import de.greluc.krt.profit.basetool.backend.model.dto.BlueprintCraftabilityDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CraftabilityGroupDto;
import de.greluc.krt.profit.basetool.backend.model.dto.CraftabilityMaterialDto;
import de.greluc.krt.profit.basetool.backend.model.dto.PersonalBlueprintResponse;
import de.greluc.krt.profit.basetool.backend.model.projection.OwnedStockSlice;
import de.greluc.krt.profit.basetool.backend.model.scwiki.Blueprint;
import de.greluc.krt.profit.basetool.backend.model.scwiki.BlueprintIngredient;
import de.greluc.krt.profit.basetool.backend.model.scwiki.BlueprintIngredientKind;
import de.greluc.krt.profit.basetool.backend.model.scwiki.BlueprintRequirementGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes, for each of the caller's owned blueprints, whether and how many times it can be crafted
 * from the caller's own stock — the answer the Personal Inventory blueprint view annotates each
 * blueprint with (#781, REQ-INV-019).
 *
 * <p>Strictly owner-scoped: the owned blueprints come from the caller's {@code sub}, the stock from
 * the caller's "My Inventory" rows ({@code user == me}, pooled across all locations), and the
 * optional refinery yield from the caller's own {@code OPEN}/{@code IN_PROGRESS} refinery orders.
 * No other user's data is ever read. Read-only and allocation-light; only RESOURCE (commodity)
 * ingredients are evaluated, ITEM ingredients are flagged "not evaluated".
 *
 * <p>Per material the recipe needs, only stock at or above a quality floor counts: the stricter of
 * the ingredient's {@code min_quality} and the lowest quality at which none of the slot's stat
 * modifiers would worsen the output (see {@link BlueprintModifierMath#noDegradationFloor}). The
 * craftable count is {@code floor(min over materials of available / required)}; the effective
 * output quality is the SCU-weighted average of the best-quality qualifying stock consumed first,
 * over one craft's requirement. Every figure is produced twice — inventory alone and with the
 * refinery yield folded in — so the UI's refinery toggle switches client-side.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlueprintCraftabilityService {

  private final PersonalBlueprintService personalBlueprintService;
  private final BlueprintProductService blueprintProductService;
  private final InventoryItemService inventoryItemService;
  private final RefineryOrderService refineryOrderService;

  /**
   * Computes the craftability of every blueprint the caller owns.
   *
   * @param ownerSub the caller's Keycloak {@code sub} (owns the personal blueprints)
   * @param userId the caller's user id (owns the inventory + refinery rows; equals the {@code sub})
   * @param includeRefinery whether to fold the caller's open refinery yield into the {@code
   *     *WithRefinery} figures; when {@code false} those equal the inventory-only figures
   * @return one craftability entry per owned blueprint; never {@code null}
   */
  @NotNull
  public List<BlueprintCraftabilityDto> computeForOwner(
      @NotNull String ownerSub, @NotNull UUID userId, boolean includeRefinery) {
    List<PersonalBlueprintResponse> owned =
        personalBlueprintService.listOwn(ownerSub, null, Pageable.unpaged()).getContent();
    if (owned.isEmpty()) {
      return List.of();
    }

    Set<String> productKeys =
        owned.stream().map(PersonalBlueprintResponse::productKey).collect(Collectors.toSet());
    Map<String, Blueprint> recipes =
        blueprintProductService.resolveRepresentativeBlueprints(productKeys);

    Map<UUID, List<OwnedStockSlice>> inventory =
        slicesPerMaterial(inventoryItemService.getOwnedStockSlices(userId));
    Map<UUID, List<OwnedStockSlice>> withRefinery = inventory;
    if (includeRefinery) {
      List<OwnedStockSlice> merged =
          new ArrayList<>(inventoryItemService.getOwnedStockSlices(userId));
      merged.addAll(refineryOrderService.getOwnedOpenRefineryYieldSlices(userId));
      withRefinery = slicesPerMaterial(merged);
    }

    List<BlueprintCraftabilityDto> out = new ArrayList<>(owned.size());
    for (PersonalBlueprintResponse blueprint : owned) {
      out.add(
          computeOne(blueprint.id(), recipes.get(blueprint.productKey()), inventory, withRefinery));
    }
    return out;
  }

  /**
   * Computes one blueprint's craftability from the pooled stock maps.
   *
   * @param blueprintId the owned blueprint id
   * @param recipe the representative recipe, or {@code null} when none resolved
   * @param inventory stock pooled per material, inventory only
   * @param withRefinery stock pooled per material, inventory plus open refinery yield
   * @return the craftability entry
   */
  @NotNull
  private BlueprintCraftabilityDto computeOne(
      @NotNull UUID blueprintId,
      @Nullable Blueprint recipe,
      @NotNull Map<UUID, List<OwnedStockSlice>> inventory,
      @NotNull Map<UUID, List<OwnedStockSlice>> withRefinery) {
    if (recipe == null) {
      return new BlueprintCraftabilityDto(
          blueprintId, false, false, false, 0, 0, null, null, List.of(), List.of());
    }

    List<BlueprintIngredient> flat = recipe.getIngredients();
    boolean hasItem = flat.stream().anyMatch(i -> i.getKind() == BlueprintIngredientKind.ITEM);

    // Aggregate the RESOURCE requirement per material (a material used in several slots is pooled;
    // its floor is the strictest of the slots' floors).
    Map<UUID, MaterialRequirement> requirements = new LinkedHashMap<>();
    for (BlueprintIngredient ingredient : flat) {
      if (ingredient.getKind() != BlueprintIngredientKind.RESOURCE) {
        continue;
      }
      Material material = ingredient.getMaterial();
      if (material == null || material.getId() == null) {
        continue;
      }
      double required = ingredient.getQuantityScu() == null ? 0.0d : ingredient.getQuantityScu();
      if (required <= 0.0d) {
        continue;
      }
      int floor = slotFloor(ingredient);
      requirements
          .computeIfAbsent(material.getId(), id -> new MaterialRequirement(id, material.getName()))
          .add(required, floor);
    }

    if (requirements.isEmpty()) {
      // ITEM-only or no resolvable RESOURCE ingredient: craftability cannot be assessed.
      return new BlueprintCraftabilityDto(
          blueprintId, true, hasItem, false, 0, 0, null, null, List.of(), List.of());
    }

    Map<UUID, MaterialResult> results = new LinkedHashMap<>();
    int craftable = Integer.MAX_VALUE;
    int craftableWithRefinery = Integer.MAX_VALUE;
    double tightest = Double.MAX_VALUE;
    double tightestWithRefinery = Double.MAX_VALUE;
    String limiting = null;
    String limitingWithRefinery = null;

    for (MaterialRequirement requirement : requirements.values()) {
      Availability inv =
          availability(
              inventory.get(requirement.materialId), requirement.floor, requirement.required);
      Availability ref =
          availability(
              withRefinery.get(requirement.materialId), requirement.floor, requirement.required);
      results.put(requirement.materialId, new MaterialResult(requirement, inv, ref));

      int countInv = (int) Math.floor(inv.available / requirement.required);
      int countRef = (int) Math.floor(ref.available / requirement.required);
      craftable = Math.min(craftable, countInv);
      craftableWithRefinery = Math.min(craftableWithRefinery, countRef);

      double ratioInv = inv.available / requirement.required;
      if (ratioInv < tightest) {
        tightest = ratioInv;
        limiting = requirement.materialName;
      }
      double ratioRef = ref.available / requirement.required;
      if (ratioRef < tightestWithRefinery) {
        tightestWithRefinery = ratioRef;
        limitingWithRefinery = requirement.materialName;
      }
    }

    List<CraftabilityMaterialDto> materials = new ArrayList<>(results.size());
    for (MaterialResult result : results.values()) {
      MaterialRequirement req = result.requirement;
      materials.add(
          new CraftabilityMaterialDto(
              req.materialId,
              req.materialName,
              round(req.required),
              req.floor,
              round(result.inventory.available),
              round(result.withRefinery.available),
              roundNullable(result.inventory.effectiveQuality),
              roundNullable(result.withRefinery.effectiveQuality),
              round(result.inventory.missing),
              round(result.withRefinery.missing),
              (int) Math.floor(result.inventory.available / req.required),
              (int) Math.floor(result.withRefinery.available / req.required)));
    }

    List<CraftabilityGroupDto> groups = buildGroups(recipe, flat, results);

    return new BlueprintCraftabilityDto(
        blueprintId,
        true,
        hasItem,
        true,
        craftable == Integer.MAX_VALUE ? 0 : craftable,
        craftableWithRefinery == Integer.MAX_VALUE ? 0 : craftableWithRefinery,
        limiting,
        limitingWithRefinery,
        groups,
        materials);
  }

  /**
   * Builds the per-requirement-group overlay in recipe order, each carrying the effective quality
   * of its limiting RESOURCE material so the frontend slider can default to it. The slot's driving
   * material is found from the owned flat ingredient list (which always carries the group
   * back-reference) rather than the group's inverse collection, so the result is independent of
   * whether Hibernate hydrated that inverse.
   *
   * @param recipe the representative recipe
   * @param flat the recipe's flat ingredient list (group back-references set)
   * @param results the per-material results computed for this recipe
   * @return the group overlays, in {@code requirementGroups} order
   */
  @NotNull
  private List<CraftabilityGroupDto> buildGroups(
      @NotNull Blueprint recipe,
      @NotNull List<BlueprintIngredient> flat,
      @NotNull Map<UUID, MaterialResult> results) {
    List<BlueprintRequirementGroup> recipeGroups = recipe.getRequirementGroups();
    List<CraftabilityGroupDto> groups = new ArrayList<>(recipeGroups.size());
    for (BlueprintRequirementGroup group : recipeGroups) {
      UUID drivingMaterialId = firstResourceMaterialId(flat, group);
      MaterialResult result = drivingMaterialId == null ? null : results.get(drivingMaterialId);
      groups.add(
          new CraftabilityGroupDto(
              drivingMaterialId,
              result == null ? null : roundNullable(result.inventory.effectiveQuality),
              result == null ? null : roundNullable(result.withRefinery.effectiveQuality)));
    }
    return groups;
  }

  /**
   * Returns the id of a slot's first resolved RESOURCE ingredient material, or {@code null}, by
   * scanning the flat ingredient list for lines that back-reference this exact group instance.
   *
   * @param flat the recipe's flat ingredient list
   * @param group the requirement group instance to match by reference
   * @return the driving material id, or {@code null} for an ITEM-only / unresolved slot
   */
  @Nullable
  private static UUID firstResourceMaterialId(
      @NotNull List<BlueprintIngredient> flat, @NotNull BlueprintRequirementGroup group) {
    for (BlueprintIngredient ingredient : flat) {
      if (ingredient.getRequirementGroup() == group
          && ingredient.getKind() == BlueprintIngredientKind.RESOURCE
          && ingredient.getMaterial() != null
          && ingredient.getMaterial().getId() != null) {
        return ingredient.getMaterial().getId();
      }
    }
    return null;
  }

  /**
   * Computes the quality floor for one ingredient line: the stricter of its {@code min_quality} and
   * its slot's no-degradation floor.
   *
   * @param ingredient the RESOURCE ingredient line
   * @return the quality floor (0..1000)
   */
  private static int slotFloor(@NotNull BlueprintIngredient ingredient) {
    BlueprintRequirementGroup group = ingredient.getRequirementGroup();
    int degradationFloor =
        group == null ? 0 : BlueprintModifierMath.noDegradationFloor(group.getModifiers());
    int minQuality = ingredient.getMinQuality() == null ? 0 : ingredient.getMinQuality();
    return Math.max(degradationFloor, minQuality);
  }

  /**
   * Computes availability for one material from its qualifying stock slices: the total SCU, the
   * shortfall against one craft, and the best-first SCU-weighted effective quality.
   *
   * @param slices the material's stock slices (may be {@code null} when none owned)
   * @param floor the quality floor below which stock does not count
   * @param required the SCU one craft needs of this material
   * @return the availability summary
   */
  @NotNull
  private static Availability availability(
      @Nullable List<OwnedStockSlice> slices, int floor, double required) {
    if (slices == null || slices.isEmpty()) {
      return new Availability(0.0d, null, required);
    }
    List<OwnedStockSlice> qualifying =
        slices.stream()
            .filter(
                s ->
                    s.quality() != null
                        && s.quality() >= floor
                        && s.totalScu() != null
                        && s.totalScu() > 0.0d)
            .sorted(Comparator.comparingInt(OwnedStockSlice::quality).reversed())
            .toList();
    double available = qualifying.stream().mapToDouble(OwnedStockSlice::totalScu).sum();
    double missing = Math.max(0.0d, required - available);

    double needed = required;
    double consumed = 0.0d;
    double weighted = 0.0d;
    for (OwnedStockSlice slice : qualifying) {
      double take = Math.min(slice.totalScu(), needed - consumed);
      if (take <= 0.0d) {
        break;
      }
      weighted += take * slice.quality();
      consumed += take;
      if (consumed >= needed) {
        break;
      }
    }
    Double effectiveQuality = consumed > 0.0d ? weighted / consumed : null;
    return new Availability(available, effectiveQuality, missing);
  }

  /**
   * Groups stock slices by material id, preserving the list per material for best-first
   * consumption.
   *
   * @param slices the flat slice list
   * @return slices keyed by material id
   */
  @NotNull
  private static Map<UUID, List<OwnedStockSlice>> slicesPerMaterial(
      @NotNull List<OwnedStockSlice> slices) {
    return slices.stream()
        .filter(s -> s.materialId() != null)
        .collect(Collectors.groupingBy(OwnedStockSlice::materialId));
  }

  /**
   * Rounds an SCU value to three decimals (the inventory amount scale).
   *
   * @param value the value
   * @return the rounded value
   */
  private static double round(double value) {
    return Math.round(value * 1000.0d) / 1000.0d;
  }

  /**
   * Rounds a nullable quality value to two decimals.
   *
   * @param value the value, or {@code null}
   * @return the rounded value, or {@code null}
   */
  @Nullable
  private static Double roundNullable(@Nullable Double value) {
    return value == null ? null : Math.round(value * 100.0d) / 100.0d;
  }

  /** Mutable accumulator for one material's pooled RESOURCE requirement across a recipe's slots. */
  private static final class MaterialRequirement {
    private final UUID materialId;
    private final String materialName;
    private double required;
    private int floor;

    private MaterialRequirement(UUID materialId, String materialName) {
      this.materialId = materialId;
      this.materialName = materialName;
    }

    private void add(double requiredScu, int slotFloor) {
      this.required += requiredScu;
      this.floor = Math.max(this.floor, slotFloor);
    }
  }

  /**
   * Availability of one material: total qualifying SCU, the effective quality of the stock consumed
   * for one craft, and the SCU shortfall against one craft.
   *
   * @param available the total qualifying SCU
   * @param effectiveQuality the best-first SCU-weighted quality, or {@code null} when none
   *     qualifies
   * @param missing the SCU short of one craft
   */
  private record Availability(
      double available, @Nullable Double effectiveQuality, double missing) {}

  /**
   * One material's requirement paired with its inventory-only and refinery-included availability.
   *
   * @param requirement the pooled requirement
   * @param inventory availability from inventory alone
   * @param withRefinery availability with the open refinery yield folded in
   */
  private record MaterialResult(
      MaterialRequirement requirement, Availability inventory, Availability withRefinery) {}
}
