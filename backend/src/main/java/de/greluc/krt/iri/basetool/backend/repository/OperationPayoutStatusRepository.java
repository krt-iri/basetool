package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.OperationPayoutStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data repository for {@link OperationPayoutStatus}. */
@Repository
public interface OperationPayoutStatusRepository
    extends JpaRepository<OperationPayoutStatus, UUID> {

  /**
   * Returns every payout-status row for the given operation, with the audit user pre-fetched so the
   * operation roll-up does not trigger an extra SELECT per row when rendering "paid out by".
   *
   * @param operationId the operation primary key
   * @return all status rows for this operation (possibly empty)
   */
  @EntityGraph(attributePaths = {"paidOutByUser"})
  List<OperationPayoutStatus> findByOperationId(UUID operationId);

  /**
   * Looks up the status row for a specific (operation, participant-key) tuple. The toggle endpoint
   * uses this to decide whether to materialize a new row or update the existing one in place.
   *
   * @param operationId the operation primary key
   * @param participantKey opaque participant key (user UUID or {@code "guest_<name>"})
   * @return the existing row, or empty if the flag has never been toggled
   */
  Optional<OperationPayoutStatus> findByOperationIdAndParticipantKey(
      UUID operationId, String participantKey);
}
