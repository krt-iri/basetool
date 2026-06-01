package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MaterialClaim;
import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link MaterialClaim}. */
@Repository
public interface MaterialClaimRepository extends JpaRepository<MaterialClaim, UUID> {

  /**
   * Returns every claim on the given order, eager-loading the material / claiming org unit / audit
   * user so the bucket view renders without an N+1. Ordered newest-first by creation instant.
   *
   * @param jobOrderId the order whose claims to load.
   * @return claims on the order, never {@code null}.
   */
  @EntityGraph(attributePaths = {"material", "claimingOrgUnit", "claimedByUser"})
  List<MaterialClaim> findByJobOrderIdOrderByCreatedAtDesc(UUID jobOrderId);

  /**
   * Returns the single claim a squadron holds on one bucket, if any — the upsert lookup that keeps
   * the one-claim-per-{@code (bucket, squadron)} invariant (the caller updates the returned row's
   * amount instead of inserting a duplicate).
   *
   * @param jobOrderId the order.
   * @param materialId the material.
   * @param qualityRequirement the quality bucket.
   * @param claimingOrgUnitId the claiming squadron.
   * @return the existing claim, or empty.
   */
  Optional<MaterialClaim> findByJobOrderIdAndMaterialIdAndQualityRequirementAndClaimingOrgUnitId(
      UUID jobOrderId,
      UUID materialId,
      QualityRequirement qualityRequirement,
      UUID claimingOrgUnitId);

  /**
   * Returns every claim on one bucket across all squadrons — used to sum the already-claimed amount
   * for the open-remaining computation and the no-overclaim guard.
   *
   * @param jobOrderId the order.
   * @param materialId the material.
   * @param qualityRequirement the quality bucket.
   * @return claims on the bucket, never {@code null}.
   */
  List<MaterialClaim> findByJobOrderIdAndMaterialIdAndQualityRequirement(
      UUID jobOrderId, UUID materialId, QualityRequirement qualityRequirement);
}
