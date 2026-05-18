package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.CheckoutType;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.AggregatedInventoryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BulkCheckoutRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemNoteUpdateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialCollectionEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateDeliveredRequest;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages inventory items — the squadron's physical stock of refined and raw materials.
 *
 * <p>Each item links to a material (UEX commodity), optionally to a user (the owner, or null for
 * shared/squadron stock), and optionally to a job order or mission. The service covers the read API
 * (aggregated per material, per user, per mission, per job-order, plus the {@code /grouped}
 * variants used by the inventory page), the create/update/delete cycle, the book-out flow (consume
 * / transfer / sell), the bulk-checkout endpoint, and the material-collection roll-up used by the
 * job-order detail page.
 *
 * <p>Concurrency-relevant: the {@link #bookOutInventoryItem} flow touches multiple {@code
 * JobOrderMaterial} rows of the same aggregate. It follows the bulk-update-after-loop pattern
 * (CLAUDE.md): the loop only mutates managed entities (Hibernate dirty-checking), material ids that
 * need a {@code @Modifying(clearAutomatically=true)} bulk update are collected in a {@code
 * Set<UUID>} and the bulk update runs ONCE after the loop. Doing the bulk update inside the loop
 * would detach the entire persistence context and cause spurious 409s on the sibling rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryItemService {

  /**
   * Tolerance used when comparing inventory amounts that are stored as {@code double}. Mirrors
   * {@code JobOrderHandoverService.QUANTITY_EPSILON} — both services compare the same quantity
   * column on {@link de.greluc.krt.iri.basetool.backend.model.InventoryItem} so they need the same
   * rounding-safe threshold. Anything below 1e-4 is floating-point noise (quantities are
   * user-edited at three decimals max), not a real residual.
   */
  private static final double QUANTITY_EPSILON = 1e-4;

  private final InventoryItemRepository inventoryItemRepository;
  private final UserRepository userRepository;
  private final MaterialRepository materialRepository;
  private final LocationRepository locationRepository;
  private final JobOrderRepository jobOrderRepository;
  private final MissionRepository missionRepository;
  private final MissionFinanceEntryRepository missionFinanceEntryRepository;
  private final MissionParticipantRepository missionParticipantRepository;
  private final InventoryItemMapper inventoryItemMapper;
  private final MaterialMapper materialMapper;
  private final SquadronScopeService squadronScopeService;

  /**
   * Aggregated per-material inventory view — used by the squadron-wide inventory page.
   *
   * @param pageable page request
   * @return paged aggregated DTOs (material + total amount + average quality)
   */
  public Page<AggregatedInventoryDto> getAggregatedInventory(Pageable pageable) {
    UUID owningSquadronId = squadronScopeService.currentSquadronId().orElse(null);
    return inventoryItemRepository
        .getAggregatedInventory(owningSquadronId, pageable)
        .map(
            obj ->
                new AggregatedInventoryDto(
                    materialMapper.toDto((Material) obj[0]),
                    obj[1] != null
                        ? Math.round(((Number) obj[1]).doubleValue() * 100.0) / 100.0
                        : 0.0,
                    obj[2] != null ? ((Number) obj[2]).doubleValue() : 0.0));
  }

  /**
   * Per-material drilldown — lists every individual inventory row for the given material. Used by
   * the inventory drilldown page.
   *
   * @param materialId material to drill into
   * @param pageable page request
   * @return paged inventory items (excludes personal items)
   * @throws NotFoundException when the material id is unknown
   */
  public Page<InventoryItemDto> getInventoryByMaterial(UUID materialId, Pageable pageable) {
    Material material =
        materialRepository
            .findById(materialId)
            .orElseThrow(() -> new NotFoundException("Material not found"));
    return inventoryItemRepository
        .findByMaterialAndPersonalFalse(material, pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * User-scoped inventory list. Excludes personal items because those have their own dedicated
   * service.
   *
   * @param userId owner id
   * @param pageable page request
   * @return paged inventory items owned by the user
   */
  public Page<InventoryItemDto> getUserInventory(UUID userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    return inventoryItemRepository.findByUser(user, pageable).map(inventoryItemMapper::toDto);
  }

  /**
   * Unfiltered convenience overload for {@link #getMyAggregatedInventory(UUID, List, Integer, List,
   * List)}.
   *
   * @param userId owner id
   * @return aggregated items grouped by material
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(UUID userId) {
    return getMyAggregatedInventory(userId, null, null, null, null);
  }

  /**
   * Job-order/mission-filtered convenience overload.
   *
   * @param userId owner id
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(UUID userId, List<UUID> jobOrderIds, List<UUID> missionIds) {
    return getMyAggregatedInventory(userId, null, null, jobOrderIds, missionIds);
  }

  /**
   * Full-filter user-scoped aggregation. Loads the user's items via the parameterized repository
   * query and groups them in memory — the {@code GroupedInventoryDto} shape is what the {@code
   * /grouped} frontend endpoint returns directly.
   *
   * @param userId owner id
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items
   * @throws NotFoundException when the user id is unknown
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getMyAggregatedInventory(
          UUID userId,
          List<UUID> materialIds,
          Integer minQuality,
          List<UUID> jobOrderIds,
          List<UUID> missionIds) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    List<InventoryItemDto> items =
        inventoryItemRepository
            .findUserByFilters(
                user,
                hasMaterials,
                hasMaterials ? materialIds : null,
                minQuality,
                hasJobOrders,
                hasJobOrders ? jobOrderIds : null,
                hasMissions,
                hasMissions ? missionIds : null,
                Pageable.unpaged())
            .getContent()
            .stream()
            .map(inventoryItemMapper::toDto)
            .toList();

    return aggregateInventoryItems(items);
  }

  /**
   * Convenience overload of {@link #getAllAggregatedInventory(List, Integer, List, List)} without
   * job-order/mission filters.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @return aggregated squadron-wide items
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getAllAggregatedInventory(List<UUID> materialIds, Integer minQuality) {
    return getAllAggregatedInventory(materialIds, minQuality, null, null);
  }

  /**
   * Squadron-wide aggregated inventory with the full filter surface. Mirrors {@link
   * #getMyAggregatedInventory} but scopes to all users (admin/logistician view).
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @return aggregated items grouped by material
   */
  public List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      getAllAggregatedInventory(
          List<UUID> materialIds,
          Integer minQuality,
          List<UUID> jobOrderIds,
          List<UUID> missionIds) {
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    UUID owningSquadronId = squadronScopeService.currentSquadronId().orElse(null);
    List<InventoryItemDto> items =
        inventoryItemRepository
            .findGlobalByFilters(
                hasMaterials,
                hasMaterials ? materialIds : null,
                minQuality,
                hasJobOrders,
                hasJobOrders ? jobOrderIds : null,
                hasMissions,
                hasMissions ? missionIds : null,
                owningSquadronId,
                Pageable.unpaged())
            .getContent()
            .stream()
            .map(inventoryItemMapper::toDto)
            .toList();

    return aggregateInventoryItems(items);
  }

  private List<de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto>
      aggregateInventoryItems(List<InventoryItemDto> items) {
    return items.stream()
        .collect(java.util.stream.Collectors.groupingBy(InventoryItemDto::material))
        .entrySet()
        .stream()
        .map(
            entry -> {
              final de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto mat =
                  entry.getKey();
              List<InventoryItemDto> matItems = entry.getValue();

              matItems.sort(
                  java.util.Comparator.<InventoryItemDto, Integer>comparing(
                          i -> i.quality() != null ? i.quality() : 0)
                      .reversed()
                      .thenComparing(
                          i ->
                              i.location() != null && i.location().name() != null
                                  ? i.location().name()
                                  : "")
                      .thenComparing(
                          java.util.Comparator.<InventoryItemDto, Double>comparing(
                                  i -> i.amount() != null ? i.amount() : 0.0)
                              .reversed()));

              double totalAmount = 0.0;
              double qualitySum = 0.0;
              int maxQuality = 0;

              for (InventoryItemDto item : matItems) {
                double amt = item.amount() != null ? item.amount() : 0.0;
                int qual = item.quality() != null ? item.quality() : 0;
                totalAmount += amt;
                qualitySum += amt * qual;
                if (qual > maxQuality) {
                  maxQuality = qual;
                }
              }

              double avgQuality = totalAmount > 0 ? qualitySum / totalAmount : 0.0;
              avgQuality = Math.round(avgQuality * 100.0) / 100.0;

              return new de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto(
                  mat, totalAmount, avgQuality, maxQuality, matItems);
            })
        .sorted(java.util.Comparator.comparing(g -> g.material().name()))
        .toList();
  }

  /**
   * Convenience overload without job-order/mission filters.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param pageable page request
   * @return paged inventory items
   */
  public Page<InventoryItemDto> getAllInventory(
      List<UUID> materialIds, Integer minQuality, Pageable pageable) {
    return getAllInventory(materialIds, minQuality, null, null, pageable);
  }

  /**
   * Flat paged squadron-wide inventory with optional filters. Not aggregated — one row per {@code
   * InventoryItem}.
   *
   * @param materialIds optional material filter
   * @param minQuality optional min-quality filter
   * @param jobOrderIds optional job order filter
   * @param missionIds optional mission filter
   * @param pageable page request
   * @return paged inventory items
   */
  public Page<InventoryItemDto> getAllInventory(
      List<UUID> materialIds,
      Integer minQuality,
      List<UUID> jobOrderIds,
      List<UUID> missionIds,
      Pageable pageable) {
    boolean hasMaterials = materialIds != null && !materialIds.isEmpty();
    boolean hasJobOrders = jobOrderIds != null && !jobOrderIds.isEmpty();
    boolean hasMissions = missionIds != null && !missionIds.isEmpty();
    UUID owningSquadronId = squadronScopeService.currentSquadronId().orElse(null);
    return inventoryItemRepository
        .findGlobalByFilters(
            hasMaterials,
            hasMaterials ? materialIds : null,
            minQuality,
            hasJobOrders,
            hasJobOrders ? jobOrderIds : null,
            hasMissions,
            hasMissions ? missionIds : null,
            owningSquadronId,
            pageable)
        .map(inventoryItemMapper::toDto);
  }

  /**
   * Creates a new inventory item. Resolves every shallow id reference (material, location, owner,
   * mission, job order) and rejects with 404 / 400 for unknown ids. Job-order link triggers an
   * eligibility check (material must match the order's material list).
   *
   * @throws NotFoundException when any referenced id is unknown
   * @throws de.greluc.krt.iri.basetool.backend.exception.BadRequestException when the material does
   *     not satisfy the job order's requirements
   */
  @Transactional
  public InventoryItemDto createInventoryItem(
      InventoryItemCreateDto dto, UUID currentUserId, boolean isAdmin) {
    UUID targetUserId = dto.userId() != null ? dto.userId() : currentUserId;
    if (!targetUserId.equals(currentUserId) && !isAdmin) {
      throw new AccessDeniedException(
          "You are not allowed to create inventory items for other users");
    }

    final User user =
        userRepository
            .findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("User not found"));
    final Material material =
        materialRepository
            .findById(dto.materialId())
            .orElseThrow(() -> new NotFoundException("Material not found"));
    final Location location =
        locationRepository
            .findById(dto.locationId())
            .orElseThrow(() -> new NotFoundException("Location not found"));

    Mission mission = null;
    if (dto.missionId() != null) {
      mission =
          missionRepository
              .findById(dto.missionId())
              .orElseThrow(() -> new NotFoundException("Mission not found"));
    }

    JobOrder jobOrder = null;
    if (dto.jobOrderId() != null) {
      jobOrder =
          jobOrderRepository
              .findById(dto.jobOrderId())
              .orElseThrow(() -> new NotFoundException("JobOrder not found"));
    }

    Boolean isPersonal = dto.personal() != null ? dto.personal() : false;

    if (Boolean.TRUE.equals(isPersonal) && (mission != null || jobOrder != null)) {
      throw new BadRequestException("Personal items cannot be assigned to a mission or job order");
    }

    java.util.List<InventoryItem> existingItems =
        inventoryItemRepository.findMatchingInventoryItem(
            user, material, location, dto.quality(), mission, jobOrder, isPersonal);

    java.util.Optional<InventoryItem> existingItemOpt = existingItems.stream().findFirst();

    if (existingItemOpt.isPresent()) {
      InventoryItem existingItem = existingItemOpt.orElseThrow();
      existingItem.setAmount(roundAmount(existingItem.getAmount() + dto.amount()));
      return inventoryItemMapper.toDto(inventoryItemRepository.save(existingItem));
    }

    InventoryItem item = new InventoryItem();
    item.setUser(user);
    item.setOwningSquadron(user.getSquadron());
    item.setMaterial(material);
    item.setLocation(location);
    item.setQuality(dto.quality());
    item.setAmount(roundAmount(dto.amount()));
    item.setPersonal(isPersonal);
    item.setMission(mission);
    item.setJobOrder(jobOrder);

    return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
  }

  /**
   * Updates the soft associations of an inventory item (mission, job order, owner). Quantity and
   * material identity are NOT mutable here — those go through {@link #bookOutInventoryItem} so the
   * audit trail stays consistent.
   *
   * @throws NotFoundException when any referenced id is unknown
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException when the supplied
   *     version is stale
   */
  @Transactional
  public InventoryItemDto updateInventoryItem(
      UUID id, InventoryItemUpdateDto dto, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (!item.getUser().getId().equals(currentUserId)) {
      if (item.getPersonal() || !isLogistician) {
        throw new AccessDeniedException("You are not allowed to update this inventory item");
      }
    }

    if (dto.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    Boolean isPersonal = dto.personal() != null ? dto.personal() : item.getPersonal();
    item.setPersonal(isPersonal);

    if (Boolean.TRUE.equals(isPersonal) && (dto.missionId() != null || dto.jobOrderId() != null)) {
      throw new BadRequestException("Personal items cannot be assigned to a mission or job order");
    }

    Material material =
        materialRepository
            .findById(dto.materialId())
            .orElseThrow(() -> new NotFoundException("Material not found"));
    item.setMaterial(material);

    Location location =
        locationRepository
            .findById(dto.locationId())
            .orElseThrow(() -> new NotFoundException("Location not found"));
    item.setLocation(location);

    item.setQuality(dto.quality());
    item.setAmount(roundAmount(dto.amount()));

    if (dto.jobOrderId() != null) {
      JobOrder jobOrder =
          jobOrderRepository
              .findById(dto.jobOrderId())
              .orElseThrow(() -> new NotFoundException("JobOrder not found"));
      item.setJobOrder(jobOrder);
    } else {
      item.setJobOrder(null);
    }

    if (dto.missionId() != null) {
      Mission mission =
          missionRepository
              .findById(dto.missionId())
              .orElseThrow(() -> new NotFoundException("Mission not found"));
      item.setMission(mission);
    } else {
      item.setMission(null);
    }

    java.util.List<InventoryItem> existingItems =
        inventoryItemRepository.findMatchingInventoryItem(
            item.getUser(),
            item.getMaterial(),
            item.getLocation(),
            item.getQuality(),
            item.getMission(),
            item.getJobOrder(),
            item.getPersonal());

    java.util.Optional<InventoryItem> existingItemOpt =
        existingItems.stream().filter(i -> !i.getId().equals(item.getId())).findFirst();

    if (existingItemOpt.isPresent()) {
      InventoryItem existingItem = existingItemOpt.orElseThrow();
      existingItem.setAmount(roundAmount(existingItem.getAmount() + item.getAmount()));
      inventoryItemRepository.delete(item);
      return inventoryItemMapper.toDto(inventoryItemRepository.save(existingItem));
    }

    return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
  }

  /**
   * Sets, updates or removes the free-text note of an inventory item.
   *
   * <p>Access rules:
   *
   * <ul>
   *   <li>The owner of the item may always modify its note.
   *   <li>A non-owner may only modify the note when {@code isLogistician} is {@code true} (i.e.
   *       they hold {@code ROLE_LOGISTICIAN} or a role that inherits it such as {@code
   *       ROLE_OFFICER}/{@code ROLE_ADMIN}).
   * </ul>
   *
   * <p>A blank or empty note is normalized to {@code null} and thus effectively removes the note.
   * Optimistic locking is enforced via the supplied {@code version}.
   *
   * @throws NotFoundException when the item is unknown
   */
  @Transactional
  public InventoryItemDto updateNote(
      UUID id, InventoryItemNoteUpdateRequest request, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    boolean isOwner = item.getUser().getId().equals(currentUserId);
    if (!isOwner && !isLogistician) {
      throw new AccessDeniedException(
          "You are not allowed to modify the note of this inventory item");
    }

    if (request.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    String normalizedNote = request.note();
    if (normalizedNote != null) {
      normalizedNote = normalizedNote.trim();
      if (normalizedNote.isEmpty()) {
        normalizedNote = null;
      }
    }
    item.setNote(normalizedNote);

    return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
  }

  /**
   * Consumes or transfers an inventory item.
   *
   * <p>The {@code type} discriminator selects: CONSUME (just decrement), TRANSFER (decrement here,
   * create a new row at the target location/owner), SELL (decrement here, create a finance entry
   * for the participant). When the post-decrement quantity is below {@link #QUANTITY_EPSILON} the
   * row is removed entirely. Fulfills attached job-order materials when the amount delivered
   * reaches the required quantity.
   *
   * <p>Follows the bulk-update-after-loop concurrency pattern: collects every {@code
   * JobOrderMaterial} id that may be ready for a clearing bulk update in a {@code Set<UUID>},
   * applies all mutations through dirty-checking inside the loop, then runs the bulk update exactly
   * once after the loop. Re-fetches the aggregate root for the completion check so {@link
   * de.greluc.krt.iri.basetool.backend.service.JobOrderService#completeJobOrderWithinTransaction}
   * sees the freshly-incremented {@code @Version}.
   *
   * @throws NotFoundException when the item is unknown
   * @throws de.greluc.krt.iri.basetool.backend.exception.BadRequestException when the requested
   *     amount exceeds the available quantity
   */
  @Transactional
  public InventoryItemDto bookOutInventoryItem(
      UUID id, InventoryItemBookOutDto dto, UUID currentUserId, boolean isAdmin) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (dto.version() != null
        && item.getVersion() != null
        && !item.getVersion().equals(dto.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    if (!item.getUser().getId().equals(currentUserId) && !isAdmin) {
      throw new AccessDeniedException("You are not allowed to book out this inventory item");
    }

    if (dto.amount() > item.getAmount()) {
      throw new BadRequestException("Cannot book out more than the available amount");
    }

    CheckoutType checkoutType = dto.type();
    if (checkoutType == null) {
      checkoutType =
          (dto.targetUserId() != null || dto.targetLocationId() != null)
              ? CheckoutType.TRANSFER
              : CheckoutType.DISCARD;
    }

    if (checkoutType == CheckoutType.SELL) {
      if (dto.terminal() == null || dto.terminal().isBlank()) {
        throw new BadRequestException("Terminal is required for selling");
      }
      if (dto.sellAmount() == null || dto.sellAmount().compareTo(java.math.BigDecimal.ZERO) < 0) {
        throw new BadRequestException("Sell amount is required and must be positive");
      }
    }

    double remainingAmount = roundAmount(item.getAmount() - dto.amount());

    if (checkoutType == CheckoutType.TRANSFER
        && (dto.targetUserId() != null || dto.targetLocationId() != null)) {
      User targetUser = item.getUser();
      if (dto.targetUserId() != null && !dto.targetUserId().equals(item.getUser().getId())) {
        targetUser =
            userRepository
                .findById(dto.targetUserId())
                .orElseThrow(() -> new NotFoundException("Target user not found"));
      }

      Location targetLocation = item.getLocation();
      if (dto.targetLocationId() != null
          && !dto.targetLocationId().equals(item.getLocation().getId())) {
        targetLocation =
            locationRepository
                .findById(dto.targetLocationId())
                .orElseThrow(() -> new NotFoundException("Target location not found"));
      }

      if (targetUser.getId().equals(item.getUser().getId())
          && targetLocation.getId().equals(item.getLocation().getId())) {
        throw new BadRequestException("Transfer must change either the user or the location");
      }

      InventoryItem newItem = new InventoryItem();
      newItem.setUser(targetUser);
      newItem.setOwningSquadron(targetUser.getSquadron());
      newItem.setMaterial(item.getMaterial());
      newItem.setLocation(targetLocation);
      newItem.setQuality(item.getQuality());
      newItem.setAmount(roundAmount(dto.amount()));
      newItem.setPersonal(item.getPersonal());
      newItem.setJobOrder(item.getJobOrder());
      newItem.setMission(item.getMission());
      InventoryItem savedNew = inventoryItemRepository.save(newItem);
      if (remainingAmount <= QUANTITY_EPSILON) {
        inventoryItemRepository.delete(item);
      } else {
        item.setAmount(remainingAmount);
        inventoryItemRepository.save(item);
      }
      return inventoryItemMapper.toDto(savedNew);
    } else if (checkoutType == CheckoutType.SELL && item.getMission() != null) {
      MissionParticipant participant =
          missionParticipantRepository
              .findByMissionIdAndUserId(item.getMission().getId(), currentUserId)
              .orElseThrow(
                  () ->
                      new BadRequestException(
                          "You must be a participant of the mission to sell its items"));

      MissionFinanceEntry entry = new MissionFinanceEntry();
      entry.setMission(item.getMission());
      entry.setParticipant(participant);
      entry.setType(FinanceType.INCOME);
      entry.setAmount(dto.sellAmount());
      entry.setNote(
          "Sale of "
              + dto.amount()
              + "x "
              + item.getMaterial().getName()
              + " at "
              + dto.terminal());
      missionFinanceEntryRepository.save(entry);
    }

    if (remainingAmount <= QUANTITY_EPSILON) { // Floating point precision safety
      inventoryItemRepository.delete(item);
      return null;
    } else {
      item.setAmount(remainingAmount);
      return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
    }
  }

  /**
   * Removes every non-personal inventory item from the database — the admin "globales Lager leeren"
   * action. Personal entries ({@code personal = true}) are kept on purpose: they belong to
   * individual users and live outside the squadron's shared stock.
   *
   * <p>Implemented as a single bulk {@code DELETE} via {@link
   * InventoryItemRepository#deleteAllNonPersonal()}. The previously load-bearing FK {@code
   * job_order_handover_item.inventory_item_id} was dropped in migration {@code V64} (handover rows
   * snapshot the material data directly), so no pre-cleanup of dependent rows is needed and no
   * {@code @Modifying(clearAutomatically = true)} loop is required — the operation is a one-shot
   * bulk statement that does not collide with any sibling-aggregate {@code @Version}.
   *
   * @return number of inventory rows deleted (0 if the global inventory was already empty)
   */
  @Transactional
  public int deleteAllGlobalInventory() {
    log.info("Bulk delete of global inventory requested");
    int removed = inventoryItemRepository.deleteAllNonPersonal();
    log.info("Bulk delete of global inventory completed: {} item(s) removed", removed);
    return removed;
  }

  /**
   * Bulk-checkout: removes all inventory items with the given IDs that belong to the authenticated
   * user. Associations to JobOrders and Missions are cleared on the managed entities inside the
   * loop (no @Modifying bulk-update inside the loop). The actual deleteAllById call happens after
   * the loop, in one batch.
   *
   * @param request the bulk checkout request containing item IDs
   * @param currentUserId the UUID of the authenticated user (JWT sub)
   */
  @Transactional
  public void bulkCheckout(BulkCheckoutRequest request, UUID currentUserId) {
    log.info(
        "Bulk checkout requested by user {} for {} items", currentUserId, request.itemIds().size());

    List<UUID> toDelete = new ArrayList<>();

    for (UUID itemId : request.itemIds()) {
      InventoryItem item =
          inventoryItemRepository
              .findByIdForUpdate(itemId)
              .orElseThrow(() -> new NotFoundException("Inventory item not found: " + itemId));

      if (!item.getUser().getId().equals(currentUserId)) {
        log.warn(
            "User {} attempted to bulk-checkout item {} owned by {}",
            currentUserId,
            itemId,
            item.getUser().getId());
        throw new AccessDeniedException(
            "You are not allowed to check out inventory item: " + itemId);
      }

      // Clear associations on the managed entity – no @Modifying query inside the loop
      item.setJobOrder(null);
      item.setMission(null);

      toDelete.add(itemId);
    }

    // Flush association changes, then delete all in one batch
    inventoryItemRepository.flush();
    inventoryItemRepository.deleteAllById(toDelete);
    log.info(
        "Bulk checkout completed: {} items removed for user {}", toDelete.size(), currentUserId);
  }

  /**
   * Returns all inventory items linked to the given job order, sorted server-side by owner name,
   * location, material name, quality (desc), quantity (desc).
   *
   * @param jobOrderId the UUID of the job order
   * @return sorted list of {@link MaterialCollectionEntryDto}
   * @throws NotFoundException when the job order is unknown
   */
  public List<MaterialCollectionEntryDto> getMaterialCollection(UUID jobOrderId) {
    jobOrderRepository
        .findById(jobOrderId)
        .orElseThrow(() -> new NotFoundException("Job order not found"));
    return inventoryItemRepository.findByJobOrderIdOrdered(jobOrderId).stream()
        .map(
            item -> {
              String ownerName =
                  item.getUser().getDisplayName() != null
                      ? item.getUser().getDisplayName()
                      : item.getUser().getUsername();
              return new MaterialCollectionEntryDto(
                  item.getId(),
                  item.getVersion() != null ? item.getVersion() : 0L,
                  ownerName,
                  item.getUser().getId(),
                  item.getLocation().getName(),
                  item.getLocation().getId(),
                  item.getMaterial().getName(),
                  item.getQuality() != null ? item.getQuality().doubleValue() : null,
                  item.getAmount(),
                  Boolean.TRUE.equals(item.getDelivered()));
            })
        .toList();
  }

  /**
   * Updates the delivered status of an inventory item. Applies optimistic locking via the version
   * field.
   *
   * @param id the UUID of the inventory item
   * @param request the update request containing delivered flag and version
   * @param currentUserId the UUID of the authenticated user
   * @param isLogistician whether the user has logistician or higher role
   * @return updated {@link InventoryItemDto}
   * @throws NotFoundException when the item is unknown
   */
  @Transactional
  public InventoryItemDto updateDelivered(
      UUID id, UpdateDeliveredRequest request, UUID currentUserId, boolean isLogistician) {
    InventoryItem item =
        inventoryItemRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Inventory item not found"));

    if (!item.getUser().getId().equals(currentUserId) && !isLogistician) {
      throw new AccessDeniedException("You are not allowed to update this inventory item");
    }

    if (item.getVersion() != null && !item.getVersion().equals(request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          InventoryItem.class, id);
    }

    item.setDelivered(request.delivered());
    return inventoryItemMapper.toDto(inventoryItemRepository.save(item));
  }

  private Double roundAmount(Double amount) {
    if (amount == null) {
      return null;
    }
    return java.math.BigDecimal.valueOf(amount)
        .setScale(3, java.math.RoundingMode.HALF_UP)
        .doubleValue();
  }
}
