package de.greluc.krt.iri.basetool.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.QualityRequirement;
import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.ClaimBucketDto;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateClaimDto;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialClaimRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.SpecialCommandRepository;
import de.greluc.krt.iri.basetool.backend.repository.SquadronRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end coverage of {@link MaterialClaimService} against the real Postgres test container: the
 * upsert persists and updates in place (one row per bucket+squadron), overclaim is rejected, and
 * the Phase-2 reassignment de-escalation withdraws claims via {@link
 * JobOrderService#reassignResponsibleOrgUnit}. Runs as an ADMIN so the service permission matrix
 * short-circuits to "allowed" and the test focuses on the invariants + reconciliation.
 */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = {"ADMIN"})
class MaterialClaimIntegrationTest {

  @Autowired private MaterialClaimService materialClaimService;
  @Autowired private JobOrderService jobOrderService;
  @Autowired private JobOrderRepository jobOrderRepository;
  @Autowired private MaterialClaimRepository materialClaimRepository;
  @Autowired private SquadronRepository squadronRepository;
  @Autowired private SpecialCommandRepository specialCommandRepository;
  @Autowired private MaterialRepository materialRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private record Fixture(UUID orderId, UUID materialId, UUID squadronAId, UUID squadronBId) {}

  private Fixture seed() {
    return transactionTemplate.execute(
        status -> {
          String tag = UUID.randomUUID().toString().substring(0, 8);
          SpecialCommand sk = new SpecialCommand();
          sk.setName("Claim-SK-" + tag);
          sk.setShorthand("S" + tag.substring(0, 3));
          sk.setProfitEligible(true);
          sk = specialCommandRepository.save(sk);

          Squadron sqA = new Squadron();
          sqA.setName("Claim-A-" + tag);
          sqA.setShorthand("A" + tag.substring(0, 3));
          sqA.setProfitEligible(true);
          sqA = squadronRepository.save(sqA);

          Squadron sqB = new Squadron();
          sqB.setName("Claim-B-" + tag);
          sqB.setShorthand("B" + tag.substring(0, 3));
          sqB = squadronRepository.save(sqB);

          Material mat = new Material();
          mat.setName("ClaimMat-" + tag);
          mat.setType(MaterialType.RAW);
          mat = materialRepository.save(mat);

          JobOrder order =
              JobOrder.builder()
                  .responsibleOrgUnit(sk)
                  .requestingOrgUnit(sqA)
                  .handle("claim-test")
                  .status(JobOrderStatus.OPEN)
                  .build();
          JobOrderMaterial line =
              JobOrderMaterial.builder().material(mat).minQuality(700).amount(10.0).build();
          order.addMaterial(line);
          order = jobOrderRepository.save(order);

          return new Fixture(order.getId(), mat.getId(), sqA.getId(), sqB.getId());
        });
  }

  @Test
  void upsert_persistsThenUpdatesInPlace_oneRowPerBucket() {
    Fixture f = seed();

    materialClaimService.upsertClaim(f.orderId(), claim(f.materialId(), f.squadronAId(), 6.0));
    ClaimBucketDto afterFirst = onlyBucket(f.orderId());
    assertThat(afterFirst.requiredAmount()).isEqualTo(10.0);
    assertThat(afterFirst.claimedAmount()).isEqualTo(6.0);
    assertThat(afterFirst.openRemaining()).isEqualTo(4.0);

    // Re-posting the same squadron's claim updates in place rather than inserting a duplicate.
    materialClaimService.upsertClaim(f.orderId(), claim(f.materialId(), f.squadronAId(), 8.0));
    ClaimBucketDto afterUpdate = onlyBucket(f.orderId());
    assertThat(afterUpdate.claimedAmount()).isEqualTo(8.0);
    assertThat(afterUpdate.openRemaining()).isEqualTo(2.0);
    assertThat(materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(f.orderId()))
        .hasSize(1);
  }

  @Test
  void overclaim_rejected() {
    Fixture f = seed();
    materialClaimService.upsertClaim(f.orderId(), claim(f.materialId(), f.squadronAId(), 8.0));

    // Squadron B requesting 5 would total 13 > the required 10.
    assertThatThrownBy(
            () ->
                materialClaimService.upsertClaim(
                    f.orderId(), claim(f.materialId(), f.squadronBId(), 5.0)))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void deEscalationToSquadron_withdrawsAllClaims() {
    Fixture f = seed();
    materialClaimService.upsertClaim(f.orderId(), claim(f.materialId(), f.squadronAId(), 8.0));
    assertThat(materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(f.orderId()))
        .isNotEmpty();

    // Reassign the SK order down to a (profit-eligible) squadron — the order becomes private, so
    // its
    // public claims are withdrawn by the reconciliation hook.
    jobOrderService.reassignResponsibleOrgUnit(f.orderId(), f.squadronAId());

    assertThat(materialClaimRepository.findByJobOrderIdOrderByCreatedAtDesc(f.orderId())).isEmpty();
  }

  private ClaimBucketDto onlyBucket(UUID orderId) {
    List<ClaimBucketDto> buckets = materialClaimService.getClaimBuckets(orderId);
    assertThat(buckets).hasSize(1);
    return buckets.get(0);
  }

  private CreateClaimDto claim(UUID materialId, UUID claimingOrgUnitId, double amount) {
    return new CreateClaimDto(materialId, QualityRequirement.GOOD, claimingOrgUnitId, amount);
  }
}
