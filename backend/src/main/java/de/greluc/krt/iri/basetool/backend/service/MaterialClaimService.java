package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.mapper.SquadronMapper;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.JobOrderType;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialClaim;
import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto;
import de.greluc.krt.iri.basetool.backend.model.dto.ClaimDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateClaimDto;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialClaimRepository;
import de.greluc.krt.iri.basetool.backend.repository.OrgUnitRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages material claims ("Eintragungen") — the way profit squadrons sign up for partial
 * quantities of a material bucket on a public Spezialkommando job order (Job-Order rework #340,
 * Phase 4 / #344).
 *
 * <p>A claim is keyed on the aggregated bucket {@code (jobOrder, material, qualityRequirement)} so
 * the same flow serves both order kinds (a {@code MATERIAL} order buckets {@link JobOrderMaterial}
 * by {@code minQuality}, an {@code ITEM} order sums {@link JobOrderItemMaterial} per quality). The
 * service enforces every invariant the data layer cannot: claims live only on SK orders, the sum
 * across squadrons never exceeds the bucket's required amount (no overclaim), and a squadron holds
 * at most one claim per bucket (a repeat post updates rather than duplicates).
 *
 * <p>Claims are an independent aggregate — there is no mapped collection on {@link JobOrder}, so
 * the reconciliation hooks ({@link #withdrawAllForOrderWithinTransaction} on SK→Squadron
 * de-escalation, {@link #withdrawOrphanedClaimsWithinTransaction} on a bucket removal) delete rows
 * through the repository without ever bumping the parent order's {@code @Version}. This sidesteps
 * the optimistic-locking traps documented in CLAUDE.md: a withdrawal runs inside the order's edit
 * transaction but touches only {@link MaterialClaim} rows, which the order's managed graph never
 * holds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MaterialClaimService {

  private final MaterialClaimRepository materialClaimRepository;
  private final JobOrderRepository jobOrderRepository;
  private final OrgUnitRepository orgUnitRepository;
  private final UserRepository userRepository;
  private final AuthHelperService authHelperService;
  private final OwnerScopeService ownerScopeService;
  private final MaterialMapper materialMapper;
  private final SquadronMapper squadronMapper;

  /**
   * Identity of one aggregated material bucket — a material at a single quality level. Shared key
   * type for the required-amount aggregation and the per-bucket claim grouping.
   *
   * @param materialId the material.
   * @param quality the quality bucket.
   */
  private record Bucket(UUID materialId, QualityRequirement quality) {}

  /**
   * Returns the full claim view of an order: one {@link ClaimBucketDto} per required material
   * bucket, carrying the required amount, the collectively-claimed amount, the open remainder and
   * the individual per-squadron claims. Visibility is gated at the controller via {@code
   * canSeeJobOrder}; a non-SK order simply has no buckets eligible for claiming, but the read still
   * returns its required buckets with empty claim lists for a uniform UI shell.
   *
   * @param jobOrderId the order to inspect.
   * @return the per-bucket claim view, never {@code null}.
   * @throws NotFoundException when the order does not exist.
   */
  public List<ClaimBucketDto> getClaimBuckets(@NotNull UUID jobOrderId) {
    return getClaimBucketsForOrder(loadOrder(jobOrderId));
  }

  /**
   * Order-taking variant of {@link #getClaimBuckets(UUID)} for callers that already hold a managed
   * {@link JobOrder} (e.g. {@code JobOrderService.mapToDtoWithStock} enriching the detail DTO with
   * claims, Phase 5 / #345) — avoids the redundant {@code findById} reload.
   *
   * @param order the managed order whose buckets + claims to project.
   * @return the per-bucket claim view, never {@code null}.
   */
  public List<ClaimBucketDto> getClaimBucketsForOrder(@NotNull JobOrder order) {
    Map<Bucket, Double> required = requiredByBucket(order);
    Map<UUID, Material> materials = materialsByBucket(order);

    Map<Bucket, List<MaterialClaim>> claimsByBucket = new LinkedHashMap<>();
    for (MaterialClaim claim :
        materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(order.getId())) {
      claimsByBucket
          .computeIfAbsent(
              new Bucket(claim.getMaterial().getId(), claim.getQualityRequirement()),
              k -> new ArrayList<>())
          .add(claim);
    }

    List<ClaimBucketDto> buckets = new ArrayList<>();
    for (Map.Entry<Bucket, Double> entry : required.entrySet()) {
      Bucket bucket = entry.getKey();
      double requiredAmount = round3(entry.getValue());
      List<MaterialClaim> claims = claimsByBucket.getOrDefault(bucket, List.of());
      double claimedAmount = round3(claims.stream().mapToDouble(MaterialClaim::getAmount).sum());
      double openRemaining = round3(Math.max(0.0, requiredAmount - claimedAmount));
      buckets.add(
          new ClaimBucketDto(
              materialMapper.toDto(materials.get(bucket.materialId())),
              bucket.quality(),
              requiredAmount,
              claimedAmount,
              openRemaining,
              claims.stream().map(this::toClaimDto).toList()));
    }
    return buckets;
  }

  /**
   * Creates or updates a squadron's claim on a bucket (upsert keyed on {@code (bucket, squadron)}).
   * Enforces every invariant: the order must be a non-terminal SK order, the caller must be allowed
   * to act for the claiming squadron, the bucket must exist on the order, and the new amount must
   * not push the bucket's total claims past its required amount.
   *
   * @param jobOrderId the order.
   * @param dto the claim payload.
   * @return the persisted claim.
   * @throws NotFoundException when the order or material is unknown.
   * @throws BadRequestException when the order is not an open SK order, the bucket does not exist,
   *     or the amount would overclaim.
   * @throws AccessDeniedException when the caller may not act for the claiming squadron.
   */
  @Transactional
  public ClaimDto upsertClaim(@NotNull UUID jobOrderId, @NotNull CreateClaimDto dto) {
    JobOrder order = loadOrder(jobOrderId);
    assertClaimable(order);
    assertCanManage(order, dto.claimingOrgUnitId());

    Bucket bucket = new Bucket(dto.materialId(), dto.qualityRequirement());
    Map<Bucket, Double> required = requiredByBucket(order);
    Double requiredAmount = required.get(bucket);
    if (requiredAmount == null) {
      throw new BadRequestException(
          "No such material bucket on this order: material="
              + dto.materialId()
              + " quality="
              + dto.qualityRequirement());
    }

    double amount = dto.amount();
    double claimedByOthers =
        materialClaimRepository
            .findByJobOrderIdAndMaterialIdAndQualityRequirement(
                jobOrderId, dto.materialId(), dto.qualityRequirement())
            .stream()
            .filter(c -> !c.getClaimingOrgUnit().getId().equals(dto.claimingOrgUnitId()))
            .mapToDouble(MaterialClaim::getAmount)
            .sum();
    if (round3(claimedByOthers + amount) > round3(requiredAmount)) {
      throw new BadRequestException(
          "Overclaim: requested "
              + amount
              + " plus already-claimed "
              + round3(claimedByOthers)
              + " exceeds the required "
              + round3(requiredAmount)
              + " for this bucket.");
    }

    MaterialClaim claim =
        materialClaimRepository
            .findByJobOrderIdAndMaterialIdAndQualityRequirementAndClaimingOrgUnitId(
                jobOrderId, dto.materialId(), dto.qualityRequirement(), dto.claimingOrgUnitId())
            .orElseGet(
                () -> {
                  MaterialClaim fresh = new MaterialClaim();
                  fresh.setJobOrder(order);
                  fresh.setMaterial(resolveMaterial(order, dto.materialId()));
                  fresh.setQualityRequirement(dto.qualityRequirement());
                  fresh.setClaimingOrgUnit(resolveClaimingOrgUnit(dto.claimingOrgUnitId()));
                  return fresh;
                });
    claim.setAmount(amount);
    authHelperService
        .currentUserId()
        .flatMap(userRepository::findById)
        .ifPresent(claim::setClaimedByUser);

    return toClaimDto(materialClaimRepository.save(claim));
  }

  /**
   * Withdraws a single claim. Same gates as {@link #upsertClaim}: the order must be a non-terminal
   * SK order and the caller must be allowed to act for the claim's squadron.
   *
   * @param jobOrderId the order the claim belongs to.
   * @param claimId the claim to withdraw.
   * @throws NotFoundException when the order or claim is unknown, or the claim is on another order.
   * @throws BadRequestException when the order is terminal or no longer an SK order.
   * @throws AccessDeniedException when the caller may not act for the claim's squadron.
   */
  @Transactional
  public void withdrawClaim(@NotNull UUID jobOrderId, @NotNull UUID claimId) {
    JobOrder order = loadOrder(jobOrderId);
    assertClaimable(order);
    MaterialClaim claim =
        materialClaimRepository
            .findById(claimId)
            .orElseThrow(() -> new NotFoundException("MaterialClaim not found: " + claimId));
    if (!claim.getJobOrder().getId().equals(jobOrderId)) {
      throw new NotFoundException(
          "MaterialClaim " + claimId + " does not belong to order " + jobOrderId);
    }
    assertCanManage(order, claim.getClaimingOrgUnit().getId());
    materialClaimRepository.delete(claim);
  }

  /**
   * Reconciliation hook (decision #10): withdraws every claim on an order, used when a Phase-2
   * reassignment de-escalates the order from an SK back to a squadron — the order becomes private,
   * so its public claims are dropped. Runs inside the reassignment transaction on the
   * already-managed order; deletes through the repository so the order's {@code @Version} is never
   * touched.
   *
   * @param order the managed order whose claims are being withdrawn.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void withdrawAllForOrderWithinTransaction(@NotNull JobOrder order) {
    List<MaterialClaim> claims =
        materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(order.getId());
    if (!claims.isEmpty()) {
      materialClaimRepository.deleteAll(claims);
    }
  }

  /**
   * Reconciliation hook (decision #6): withdraws claims whose bucket no longer exists on the order,
   * used after an order edit removes a material line (or, finalized in Phase 7, an item-derived
   * bucket). Runs inside the edit transaction; deletes through the repository so the order's
   * {@code @Version} is never touched.
   *
   * @param order the managed order whose buckets define which claims survive.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void withdrawOrphanedClaimsWithinTransaction(@NotNull JobOrder order) {
    Map<Bucket, Double> required = requiredByBucket(order);
    List<MaterialClaim> orphaned =
        materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(order.getId()).stream()
            .filter(
                c ->
                    !required.containsKey(
                        new Bucket(c.getMaterial().getId(), c.getQualityRequirement())))
            .toList();
    if (!orphaned.isEmpty()) {
      materialClaimRepository.deleteAll(orphaned);
    }
  }

  /**
   * Computes the required amount per material bucket for either order kind: an {@code ITEM} order
   * sums each {@link JobOrderItemMaterial#getRequiredQuantity()} per {@code (material, quality)}; a
   * {@code MATERIAL} order sums each {@link JobOrderMaterial#getAmount()} with the bucket derived
   * from {@code minQuality} ({@code GOOD} when a 700-floor is set, {@code NONE} otherwise).
   *
   * @param order the order.
   * @return required amount keyed by bucket, insertion-ordered for stable rendering.
   */
  private Map<Bucket, Double> requiredByBucket(JobOrder order) {
    Map<Bucket, Double> required = new LinkedHashMap<>();
    if (order.getType() == JobOrderType.ITEM) {
      for (JobOrderItem item : order.getItems()) {
        for (JobOrderItemMaterial req : item.getMaterials()) {
          required.merge(
              new Bucket(req.getMaterial().getId(), req.getQualityRequirement()),
              req.getRequiredQuantity() == null ? 0.0 : req.getRequiredQuantity(),
              Double::sum);
        }
      }
    } else {
      for (JobOrderMaterial mat : order.getMaterials()) {
        QualityRequirement quality =
            mat.getMinQuality() != null ? QualityRequirement.GOOD : QualityRequirement.NONE;
        required.merge(
            new Bucket(mat.getMaterial().getId(), quality),
            mat.getAmount() == null ? 0.0 : mat.getAmount(),
            Double::sum);
      }
    }
    return required;
  }

  /**
   * Builds a material lookup for the buckets of an order so the bucket DTO can carry the full
   * {@link de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto} without a second query per
   * row.
   *
   * @param order the order.
   * @return material id → material, for every material referenced by a bucket.
   */
  private Map<UUID, Material> materialsByBucket(JobOrder order) {
    Map<UUID, Material> materials = new LinkedHashMap<>();
    if (order.getType() == JobOrderType.ITEM) {
      for (JobOrderItem item : order.getItems()) {
        for (JobOrderItemMaterial req : item.getMaterials()) {
          materials.putIfAbsent(req.getMaterial().getId(), req.getMaterial());
        }
      }
    } else {
      for (JobOrderMaterial mat : order.getMaterials()) {
        materials.putIfAbsent(mat.getMaterial().getId(), mat.getMaterial());
      }
    }
    return materials;
  }

  /**
   * Asserts the order accepts claim mutations: it must be responsible to a Spezialkommando (claims
   * are public-SK-only) and not in a terminal status (terminal freezes claims read-only for
   * history, decision #6).
   *
   * @param order the order.
   * @throws BadRequestException when the order is not a claimable SK order.
   */
  private void assertClaimable(JobOrder order) {
    OrgUnit responsible = order.getResponsibleOrgUnit();
    if (responsible == null || responsible.getKind() != OrgUnitKind.SPECIAL_COMMAND) {
      throw new BadRequestException(
          "Material claims are only allowed on Spezialkommando orders; order "
              + order.getId()
              + " is not responsible to an SK.");
    }
    if (order.getStatus() == JobOrderStatus.COMPLETED
        || order.getStatus() == JobOrderStatus.REJECTED) {
      throw new BadRequestException(
          "Order " + order.getId() + " is in a terminal status; its claims are frozen.");
    }
  }

  /**
   * Enforces the claim permission matrix (decision #8): an admin or an authority of the responsible
   * SK (a logistician/officer holding contextual authority on that SK) may manage any claim; a
   * squadron's logistician/officer may manage only claims for their own squadron ({@link
   * AuthHelperService#canEditOrgUnit}). The bare {@code hasRole('LOGISTICIAN')} controller gate has
   * already filtered out anyone below logistician.
   *
   * @param order the order whose responsible SK defines the elevated authority.
   * @param claimingOrgUnitId the squadron the claim is for.
   * @throws AccessDeniedException when the caller may neither manage any claim nor act for this
   *     squadron.
   */
  private void assertCanManage(JobOrder order, UUID claimingOrgUnitId) {
    if (authHelperService.isAdmin()) {
      return;
    }
    UUID responsibleSkId = order.getResponsibleOrgUnit().getId();
    boolean managesResponsibleSk =
        ownerScopeService.hasRoleInOrgUnit(responsibleSkId, "LOGISTICIAN");
    boolean managesOwnSquadron = authHelperService.canEditOrgUnit(claimingOrgUnitId);
    if (!managesResponsibleSk && !managesOwnSquadron) {
      throw new AccessDeniedException(
          "You may only manage claims for your own squadron, or any claim as an authority of the"
              + " responsible Spezialkommando.");
    }
  }

  /**
   * Loads an order with its materials/items eager-fetched (via the repository's detail graph) so
   * the bucket aggregation does not lazy-load row by row.
   *
   * @param jobOrderId the order id.
   * @return the managed order.
   * @throws NotFoundException when the order does not exist.
   */
  private JobOrder loadOrder(UUID jobOrderId) {
    return jobOrderRepository
        .findById(jobOrderId)
        .orElseThrow(() -> new NotFoundException("JobOrder not found: " + jobOrderId));
  }

  /**
   * Resolves a material referenced by a create payload, requiring it to actually be a bucket on the
   * order (the bucket existence is re-checked by the caller via {@code requiredByBucket}; this only
   * loads the managed entity).
   *
   * @param order the order the claim is on.
   * @param materialId the material id.
   * @return the managed material instance from the order's buckets.
   * @throws BadRequestException when the material is not part of the order.
   */
  private Material resolveMaterial(JobOrder order, UUID materialId) {
    Material material = materialsByBucket(order).get(materialId);
    if (material == null) {
      throw new BadRequestException(
          "Material " + materialId + " is not part of order " + order.getId());
    }
    return material;
  }

  /**
   * Resolves the claiming org unit and validates it is a squadron — claims are made by squadrons,
   * never by Spezialkommandos.
   *
   * @param claimingOrgUnitId the org unit id from the payload.
   * @return the managed squadron-kind org unit.
   * @throws BadRequestException when the id is unknown or not a squadron.
   */
  private OrgUnit resolveClaimingOrgUnit(UUID claimingOrgUnitId) {
    OrgUnit orgUnit =
        orgUnitRepository
            .findById(claimingOrgUnitId)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "claimingOrgUnitId does not resolve to a known org unit: "
                            + claimingOrgUnitId));
    if (orgUnit.getKind() != OrgUnitKind.SQUADRON) {
      throw new BadRequestException(
          "Only squadrons may claim material; " + claimingOrgUnitId + " is not a squadron.");
    }
    return orgUnit;
  }

  /**
   * Projects a persisted claim to its API DTO. Exposes the claiming squadron as a slim reference
   * and the audit user as a bare id (never a name, so no PII crosses the boundary).
   *
   * @param claim the persisted claim.
   * @return the DTO.
   */
  private ClaimDto toClaimDto(MaterialClaim claim) {
    return new ClaimDto(
        claim.getId(),
        squadronMapper.orgUnitToReferenceDto(claim.getClaimingOrgUnit()),
        claim.getAmount(),
        claim.getClaimedByUser() != null ? claim.getClaimedByUser().getId() : null,
        claim.getCreatedAt(),
        claim.getVersion());
  }

  /**
   * Rounds a quantity to the 0.001 precision the UI uses, killing the floating-point noise that
   * accumulates in summed SCU quantities (same rationale as the order-derivation rounding).
   *
   * @param value the raw quantity.
   * @return the value rounded to three decimals.
   */
  private static double round3(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
