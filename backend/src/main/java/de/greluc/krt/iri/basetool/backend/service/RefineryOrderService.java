package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.RefineryYield;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreDto;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderStoreItemDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryYieldRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus completion (store) for refinery orders.
 *
 * <p>A refinery order tracks a player's ore-to-refined-good run at a specific terminal: input
 * materials and quantities, refining method, expected output, expenses and sale proceeds. The
 * service enforces "owner can edit / logistician can edit anyone" at the method boundary because
 * the rule is per-resource (the role-only check in {@code @PreAuthorize} can't see the order's
 * owner). The {@code store} operation finalizes a completed order by creating inventory items for
 * each output material and clearing the order's open status.
 *
 * <p>Location validation: refinery orders can only target locations that actually host a refinery.
 * The check runs at create + update time so a stale location pick surfaces as a 400 with a
 * localized message instead of silently producing an unreachable order.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefineryOrderService {

  private final RefineryOrderRepository refineryOrderRepository;
  private final UserRepository userRepository;
  private final LocationRepository locationRepository;
  private final MissionRepository missionRepository;
  private final RefiningMethodRepository refiningMethodRepository;
  private final MaterialRepository materialRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final JobOrderRepository jobOrderRepository;
  private final RefineryYieldRepository refineryYieldRepository;
  private final OwnerScopeService ownerScopeService;

  /**
   * Owner-scoped paged list with optional status filter.
   *
   * @param userId owner id
   * @param statuses optional status filter; null/empty means "all statuses"
   * @param pageable page request
   * @return paged orders owned by the user
   */
  public Page<RefineryOrder> getMyRefineryOrders(
      @NotNull UUID userId,
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      @NotNull Pageable pageable) {
    if (statuses != null && !statuses.isEmpty()) {
      return refineryOrderRepository.findByOwnerIdAndStatusIn(userId, statuses, pageable);
    }
    return refineryOrderRepository.findByOwnerId(userId, pageable);
  }

  /**
   * Convenience overload without status filter.
   *
   * @param userId owner id
   * @param pageable page request
   * @return paged orders owned by the user
   */
  public Page<RefineryOrder> getMyRefineryOrders(@NotNull UUID userId, @NotNull Pageable pageable) {
    return refineryOrderRepository.findByOwnerId(userId, pageable);
  }

  /**
   * Lists all refinery orders linked to a mission (used by the mission finance roll-up).
   *
   * @param missionId mission id
   * @return all linked orders
   */
  public List<RefineryOrder> getMissionRefineryOrders(@NotNull UUID missionId) {
    return refineryOrderRepository.findByMissionId(missionId);
  }

  /**
   * Lists the orders that BOTH belong to the given mission AND are owned by the given user. Used by
   * the participant-scoped mission detail view so participants only see their own refinery lines.
   *
   * @param missionId mission id
   * @param userId owner id
   * @return matching orders
   */
  public List<RefineryOrder> getMissionRefineryOrders(
      @NotNull UUID missionId, @NotNull UUID userId) {
    return refineryOrderRepository.findByMissionIdAndOwnerId(missionId, userId);
  }

  /**
   * Squadron-wide paged list with optional status filter (admin/logistician view).
   *
   * @param statuses optional status filter
   * @param pageable page request
   * @return paged orders across all users
   */
  public Page<RefineryOrder> getAllRefineryOrders(
      List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses,
      @NotNull Pageable pageable) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    if (statuses != null && !statuses.isEmpty()) {
      return refineryOrderRepository.findByStatusInScoped(
          statuses,
          scope.adminAllScope(),
          scope.activeOrgUnitId(),
          scope.memberOrgUnitIds(),
          pageable);
    }
    return refineryOrderRepository.findAllScoped(
        scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds(), pageable);
  }

  /**
   * Convenience overload without status filter.
   *
   * @param pageable page request
   * @return paged orders across all users
   */
  public Page<RefineryOrder> getAllRefineryOrders(@NotNull Pageable pageable) {
    ScopePredicate scope = ownerScopeService.currentScopePredicate();
    return refineryOrderRepository.findAllScoped(
        scope.adminAllScope(), scope.activeOrgUnitId(), scope.memberOrgUnitIds(), pageable);
  }

  /**
   * Returns the order.
   *
   * @param id refinery order primary key
   * @return the order
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no match
   */
  public RefineryOrder getRefineryOrder(@NotNull UUID id) {
    return refineryOrderRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "error.refinery_order.not_found"));
  }

  /**
   * Persists a new refinery order owned by the given user. Resolves and validates every shallow
   * reference in the payload (location, mission, refining method, materials in goods) and rejects
   * with 404 / 400 if any id is missing or unknown. The location must host a refinery — picking a
   * regular city is rejected explicitly.
   *
   * @param userId owner id
   * @param order transient entity with shallow id-only references
   * @param owningOrgUnitId optional R5.d picker output: the {@link
   *     de.greluc.krt.iri.basetool.backend.model.OrgUnit} on whose stock the new order should land.
   *     When {@code null}, the service stamps the order owner's home Staffel (legacy behaviour).
   *     When non-null, must point at an org unit the order owner is a member of — {@link
   *     OwnerScopeService#resolveSquadronForPickerOutput} performs the validation and rejects
   *     unknown / foreign / Spezialkommando selections with {@link
   *     de.greluc.krt.iri.basetool.backend.exception.BadRequestException}.
   * @return the persisted order
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when any referenced id
   *     is unknown
   * @throws de.greluc.krt.iri.basetool.backend.exception.BadRequestException when the chosen
   *     location does not host a refinery, or the picker output is not a valid membership of the
   *     order owner
   */
  @Transactional
  public RefineryOrder createRefineryOrder(
      @NotNull UUID userId, @NotNull RefineryOrder order, UUID owningOrgUnitId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                        "error.user.not_found"));

    order.setOwner(user);
    order.setOwningOrgUnit(ownerScopeService.resolveOrgUnitForPickerOutput(user, owningOrgUnitId));

    if (order.getLocation() != null && order.getLocation().getId() != null) {
      order.setLocation(
          locationRepository
              .findById(order.getLocation().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.location.not_found")));
      validateLocationHasRefinery(order.getLocation());
    } else {
      throw new de.greluc.krt.iri.basetool.backend.exception.BadRequestException(
          "error.refinery_order.location_required");
    }

    if (order.getMission() != null && order.getMission().getId() != null) {
      order.setMission(
          missionRepository
              .findById(order.getMission().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.mission.not_found")));
    } else {
      order.setMission(null);
    }

    if (order.getRefiningMethod() != null && order.getRefiningMethod().getId() != null) {
      order.setRefiningMethod(
          refiningMethodRepository
              .findById(order.getRefiningMethod().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.refining_method.not_found")));
    } else {
      order.setRefiningMethod(null);
    }

    // Handle Goods relationships
    if (order.getGoods() != null) {
      order
          .getGoods()
          .forEach(
              good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                  de.greluc.krt.iri.basetool.backend.model.Material inMat =
                      materialRepository
                          .findById(good.getInputMaterial().getId())
                          .orElseThrow(
                              () ->
                                  new de.greluc.krt.iri.basetool.backend.exception
                                      .NotFoundException("error.material.input.not_found"));

                  if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW
                      && !Boolean.TRUE.equals(inMat.getIsManualRawMaterial())) {
                    throw new IllegalArgumentException(
                        "Refinery goods input must be of type RAW. Material '"
                            + inMat.getName()
                            + "' is "
                            + inMat.getType());
                  }
                  good.setInputMaterial(inMat);

                  if (good.getOutputMaterial() != null
                      && good.getOutputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material outMat =
                        materialRepository
                            .findById(good.getOutputMaterial().getId())
                            .orElseThrow(
                                () ->
                                    new de.greluc.krt.iri.basetool.backend.exception
                                        .NotFoundException("error.material.output.not_found"));

                    if (inMat.getRefinedMaterial() != null
                        && !outMat.getId().equals(inMat.getRefinedMaterial().getId())) {
                      throw new IllegalArgumentException(
                          "Output material must match the refined material of the input material.");
                    }

                    good.setOutputMaterial(outMat);
                  } else {
                    if (inMat.getRefinedMaterial() != null) {
                      good.setOutputMaterial(inMat.getRefinedMaterial());
                    } else {
                      good.setOutputMaterial(inMat);
                    }
                  }
                } else {
                  throw new de.greluc.krt.iri.basetool.backend.exception.BadRequestException(
                      "error.refinery_order.input_material_required");
                }
                good.setRefineryOrder(order);
              });
    }

    if (order.getStartedAt() == null) {
      order.setStartedAt(java.time.Instant.now());
    }

    // Monetary fields (expenses, otherExpenses, oreSales) are optional. Both null and 0
    // are treated semantically as "not set" and persisted as null, so the frontend never
    // has to distinguish between "empty" and "0" and the columns stay cleanly empty in
    // the DB when the user has not entered a value. The profit calculation
    // (see RefineryOrder#getProfit) already treats null as 0.
    order.setExpenses(zeroToNull(order.getExpenses()));
    order.setOtherExpenses(zeroToNull(order.getOtherExpenses()));
    order.setOreSales(zeroToNull(order.getOreSales()));

    return refineryOrderRepository.save(order);
  }

  private static Double zeroToNull(Double value) {
    if (value == null) {
      return null;
    }
    return value == 0.0 ? null : value;
  }

  /**
   * Updates an existing refinery order. Enforces "must be owner OR logistician" explicitly (the
   * role-only {@code @PreAuthorize} cannot see per-resource ownership). Validates the
   * optimistic-lock version and the same shallow-reference resolution as {@link
   * #createRefineryOrder}. The goods list is replaced wholesale; the old rows are orphan-removed.
   *
   * @throws AccessDeniedException when the caller is neither owner nor logistician
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public RefineryOrder updateRefineryOrder(
      @NotNull UUID userId,
      @NotNull UUID orderId,
      @NotNull RefineryOrder details,
      boolean isLogistician) {
    RefineryOrder order = getRefineryOrder(orderId);

    if (details.getVersion() != null && !order.getVersion().equals(details.getVersion())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          RefineryOrder.class, orderId);
    }

    if (!isLogistician
        && (order.getOwner() == null
            || order.getOwner().getId() == null
            || !order.getOwner().getId().equals(userId))) {
      throw new AccessDeniedException("Access denied: You do not own this refinery order");
    }

    if (details.getLocation() != null && details.getLocation().getId() != null) {
      order.setLocation(
          locationRepository
              .findById(details.getLocation().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.location.not_found")));
      validateLocationHasRefinery(order.getLocation());
    }

    if (details.getMission() != null && details.getMission().getId() != null) {
      order.setMission(
          missionRepository
              .findById(details.getMission().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.mission.not_found")));
    } else if (details.getMission() == null) {
      order.setMission(null);
    }

    if (details.getRefiningMethod() != null && details.getRefiningMethod().getId() != null) {
      order.setRefiningMethod(
          refiningMethodRepository
              .findById(details.getRefiningMethod().getId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "error.refining_method.not_found")));
    } else if (details.getRefiningMethod() == null) {
      order.setRefiningMethod(null);
    }

    order.setStartedAt(
        details.getStartedAt() != null ? details.getStartedAt() : java.time.Instant.now());
    order.setDurationMinutes(details.getDurationMinutes());
    // Money fields: 0 is treated as "not set" and persisted as null (see createRefineryOrder).
    order.setExpenses(zeroToNull(details.getExpenses()));
    order.setOtherExpenses(zeroToNull(details.getOtherExpenses()));
    order.setOreSales(zeroToNull(details.getOreSales()));
    if (details.getStatus() != null) {
      order.setStatus(details.getStatus());
    }

    // Update goods
    if (details.getGoods() != null) {
      order.getGoods().clear();
      details
          .getGoods()
          .forEach(
              good -> {
                if (good.getInputMaterial() != null && good.getInputMaterial().getId() != null) {
                  de.greluc.krt.iri.basetool.backend.model.Material inMat =
                      materialRepository
                          .findById(good.getInputMaterial().getId())
                          .orElseThrow(
                              () ->
                                  new de.greluc.krt.iri.basetool.backend.exception
                                      .NotFoundException("error.material.input.not_found"));

                  if (inMat.getType() != de.greluc.krt.iri.basetool.backend.model.MaterialType.RAW
                      && !Boolean.TRUE.equals(inMat.getIsManualRawMaterial())) {
                    throw new IllegalArgumentException(
                        "Refinery goods input must be of type RAW. Material '"
                            + inMat.getName()
                            + "' is "
                            + inMat.getType());
                  }
                  good.setInputMaterial(inMat);

                  if (good.getOutputMaterial() != null
                      && good.getOutputMaterial().getId() != null) {
                    de.greluc.krt.iri.basetool.backend.model.Material outMat =
                        materialRepository
                            .findById(good.getOutputMaterial().getId())
                            .orElseThrow(
                                () ->
                                    new de.greluc.krt.iri.basetool.backend.exception
                                        .NotFoundException("error.material.output.not_found"));

                    if (inMat.getRefinedMaterial() != null
                        && !outMat.getId().equals(inMat.getRefinedMaterial().getId())) {
                      throw new IllegalArgumentException(
                          "Output material must match the refined material of the input material.");
                    }

                    good.setOutputMaterial(outMat);
                  } else {
                    if (inMat.getRefinedMaterial() != null) {
                      good.setOutputMaterial(inMat.getRefinedMaterial());
                    } else {
                      good.setOutputMaterial(inMat);
                    }
                  }
                } else {
                  throw new de.greluc.krt.iri.basetool.backend.exception.BadRequestException(
                      "error.refinery_order.input_material_required");
                }
                good.setRefineryOrder(order);
                order.getGoods().add(good);
              });
    }

    return refineryOrderRepository.save(order);
  }

  /**
   * Cancels (soft-deletes) a refinery order. Same owner-vs-logistician gate as {@link
   * #updateRefineryOrder}.
   *
   * @throws AccessDeniedException when the caller is not allowed to delete this order
   */
  @Transactional
  public void deleteRefineryOrder(
      @NotNull UUID userId, @NotNull UUID orderId, boolean isLogistician) {
    RefineryOrder order = getRefineryOrder(orderId);

    if (!isLogistician
        && (order.getOwner() == null
            || order.getOwner().getId() == null
            || !order.getOwner().getId().equals(userId))) {
      throw new AccessDeniedException("Access denied: You do not own this refinery order");
    }

    order.setStatus(de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.CANCELED);
    refineryOrderRepository.save(order);
  }

  /**
   * Completes a refinery order by creating inventory items for each output material.
   *
   * <p>The refinery rounding mode setting controls how fractional output quantities are rounded
   * (see {@code SystemSettingService}). Each output material becomes one inventory row owned by the
   * configured recipient (typically the order's owner, optionally redirected to a different user /
   * job order from the store form).
   *
   * @throws AccessDeniedException when the caller is neither owner nor logistician
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when the order or any
   *     referenced id is unknown
   * @throws de.greluc.krt.iri.basetool.backend.exception.BadRequestException when the order is
   *     already stored or has no output goods
   */
  @Transactional
  public void storeRefineryOrder(
      @NotNull UUID userId,
      @NotNull UUID orderId,
      @NotNull RefineryOrderStoreDto dto,
      boolean isLogistician) {
    RefineryOrder order = getRefineryOrder(orderId);

    if (order.getStatus()
        == de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.COMPLETED) {
      throw new IllegalStateException("Refinery order is already completed and stored.");
    }

    if (!isLogistician
        && (order.getOwner() == null
            || order.getOwner().getId() == null
            || !order.getOwner().getId().equals(userId))) {
      throw new AccessDeniedException("Access denied: You do not own this refinery order");
    }

    for (RefineryOrderStoreItemDto itemDto : dto.items()) {
      de.greluc.krt.iri.basetool.backend.model.Material mat =
          materialRepository
              .findById(itemDto.materialId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "Material not found: " + itemDto.materialId()));

      de.greluc.krt.iri.basetool.backend.model.Location loc =
          locationRepository
              .findById(itemDto.locationId())
              .orElseThrow(
                  () ->
                      new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                          "Location not found: " + itemDto.locationId()));

      User assignee;
      if (itemDto.userId() != null) {
        assignee =
            userRepository
                .findById(itemDto.userId())
                .orElseThrow(
                    () ->
                        new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                            "User not found: " + itemDto.userId()));
      } else {
        assignee = order.getOwner();
      }

      de.greluc.krt.iri.basetool.backend.model.JobOrder jobOrder = null;
      if (itemDto.jobOrderId() != null) {
        jobOrder =
            jobOrderRepository
                .findById(itemDto.jobOrderId())
                .orElseThrow(
                    () ->
                        new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                            "JobOrder not found: " + itemDto.jobOrderId()));
      }

      java.util.List<InventoryItem> existingItems =
          inventoryItemRepository.findMatchingInventoryItem(
              assignee, mat, loc, itemDto.quality(), order.getMission(), jobOrder, false);

      java.util.Optional<InventoryItem> existingItemOpt = existingItems.stream().findFirst();

      // Why: The amount the user enters in the store dialog is the authoritative
      // amount (it overrides the originally calculated output amount of the refinery
      // order). The note is optionally propagated to the resulting InventoryItem so
      // the user can attach storage remarks directly to the inventory item.
      String incomingNote = normalizeNote(itemDto.note());

      if (existingItemOpt.isPresent()) {
        InventoryItem existingItem = existingItemOpt.orElseThrow();
        existingItem.setAmount(existingItem.getAmount() + itemDto.amount());
        if (incomingNote != null) {
          String existingNote = existingItem.getNote();
          if (existingNote == null || existingNote.isBlank()) {
            existingItem.setNote(incomingNote);
          } else if (!existingNote.contains(incomingNote)) {
            // Keep the existing note and append the new one so that no
            // information is lost.
            String combined = existingNote + "\n" + incomingNote;
            if (combined.length() > 1000) {
              combined = combined.substring(0, 1000);
            }
            existingItem.setNote(combined);
          }
        }
        inventoryItemRepository.save(existingItem);
      } else {
        InventoryItem item = new InventoryItem();
        item.setUser(assignee);
        // R6.b: route the stamp through OwnerScopeService, which reads the assignee's
        // Staffel membership from org_unit_membership (post-R9 D3 / V101). The store form has
        // no owner picker (refinery STORE is admin-driven and predates the R5.d picker wave),
        // so {@code owningOrgUnitId} is passed as {@code null} — the resolver auto-stamps when
        // the assignee has exactly one org-unit membership (today's 100% case) and surfaces a
        // clean 400 ("owningOrgUnitId is required") for multi-membership assignees. The latter
        // case currently can't be resolved from the UI; widening the store form with a
        // per-output picker is tracked as a follow-up to the SK §5.5 stamping wave.
        item.setOwningOrgUnit(ownerScopeService.resolveOrgUnitForPickerOutput(assignee, null));
        item.setJobOrder(jobOrder);
        item.setMaterial(mat);
        item.setLocation(loc);
        item.setQuality(itemDto.quality());
        item.setAmount(itemDto.amount());
        item.setMission(order.getMission());
        item.setNote(incomingNote);

        inventoryItemRepository.save(item);
      }

      // Write the adjusted amount back into the refinery order so that the
      // actually stored output amount is documented there as well.
      // Match by output material; if multiple identical goods exist, the
      // first not-yet-updated entry is taken.
      updateGoodOutputQuantity(order, itemDto);
    }

    order.setStatus(de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus.COMPLETED);
    refineryOrderRepository.save(order);
  }

  private static String normalizeNote(String note) {
    if (note == null) {
      return null;
    }
    String trimmed = note.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Writes the user's final storage amount back into the associated {@link
   * de.greluc.krt.iri.basetool.backend.model.RefineryGood}. This persists the manual correction of
   * the output amount in the refinery order itself (e.g. when the actual refinery output deviates
   * from the forecast). For SCU materials the SCU input is converted back into units (x100) so the
   * {@code outputQuantity} field stays uniformly tracked in units.
   */
  private void updateGoodOutputQuantity(RefineryOrder order, RefineryOrderStoreItemDto itemDto) {
    if (order.getGoods() == null
        || itemDto == null
        || itemDto.materialId() == null
        || itemDto.amount() == null) {
      return;
    }
    for (de.greluc.krt.iri.basetool.backend.model.RefineryGood good : order.getGoods()) {
      if (good.getOutputMaterial() == null || good.getOutputMaterial().getId() == null) {
        continue;
      }
      if (!good.getOutputMaterial().getId().equals(itemDto.materialId())) {
        continue;
      }

      double amount = itemDto.amount();
      String quantityTypeName =
          good.getOutputMaterial().getQuantityType() != null
              ? good.getOutputMaterial().getQuantityType().name()
              : null;
      long rawNew;
      if ("SCU".equals(quantityTypeName)) {
        rawNew = Math.round(amount * 100.0d);
      } else {
        rawNew = Math.round(amount);
      }
      // Respect @Min(1) on outputQuantity: 0 would be an invalid value.
      int clamped = (int) Math.max(1L, Math.min(rawNew, Integer.MAX_VALUE));
      good.setOutputQuantity(clamped);
      return;
    }
  }

  /**
   * Returns a {@code materialId → yieldBonusPercent} map for the refinery sitting at {@code
   * location}. The value semantics come straight from UEX: a positive integer is a bonus, a
   * negative integer is a malus, both expressed in percent (5 = +5%, -3 = -3%). An empty map means
   * "no UEX yield data is known for this location" (either the location was never picked, or the
   * UEX universe sync has not yet matched the location's city/space-station name to a terminal that
   * has yield rows).
   *
   * <p>Used by the controller to enrich {@code RefineryGoodDto.yieldBonusPercent} on outbound
   * payloads and to feed the detail page's reactive bonus display in the form. Same lookup runs for
   * every order detail render and every order write, so the underlying query is bounded by the
   * number of yield rows at one terminal (small).
   *
   * @param location the order's chosen location, may be {@code null}
   * @return map keyed by material UUID, never {@code null}
   */
  public Map<UUID, Integer> getYieldBonusByMaterialForLocation(Location location) {
    if (location == null) {
      return Map.of();
    }
    String cityName = location.getCity() != null ? location.getCity().getName() : null;
    String stationName =
        location.getSpaceStation() != null ? location.getSpaceStation().getName() : null;
    if (cityName == null && stationName == null) {
      return Map.of();
    }
    Map<UUID, Integer> result = new HashMap<>();
    for (RefineryYield yield : refineryYieldRepository.findAllForLocation(cityName, stationName)) {
      if (yield.getMaterial() == null || yield.getMaterial().getId() == null) {
        continue;
      }
      result.putIfAbsent(yield.getMaterial().getId(), yield.getYieldBonus());
    }
    return result;
  }

  /**
   * Convenience overload that resolves {@code locationId} via {@link LocationRepository} and
   * delegates to {@link #getYieldBonusByMaterialForLocation(Location)}. Returns an empty map when
   * the id is {@code null} or unknown — the caller (typically the AJAX endpoint that refreshes the
   * detail page's yield badges after the user picks a new refinery) treats "unknown location" the
   * same as "no yield data", so a 404 would only force redundant error handling on the client.
   *
   * @param locationId target location id; may be {@code null}
   * @return per-material yield bonus map for the location, never {@code null}
   */
  public Map<UUID, Integer> getYieldBonusByMaterialForLocationId(UUID locationId) {
    if (locationId == null) {
      return Map.of();
    }
    return locationRepository
        .findById(locationId)
        .map(this::getYieldBonusByMaterialForLocation)
        .orElseGet(Map::of);
  }

  private void validateLocationHasRefinery(
      de.greluc.krt.iri.basetool.backend.model.Location location) {
    boolean hasRefinery = false;
    if (location.getCity() != null && Boolean.TRUE.equals(location.getCity().getHasRefinery())) {
      hasRefinery = true;
    } else if (location.getSpaceStation() != null
        && Boolean.TRUE.equals(location.getSpaceStation().getHasRefinery())) {
      hasRefinery = true;
    }
    if (!hasRefinery) {
      throw new IllegalArgumentException("Selected location does not have a refinery.");
    }
  }
}
