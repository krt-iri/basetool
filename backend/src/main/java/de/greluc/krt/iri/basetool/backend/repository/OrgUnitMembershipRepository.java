package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership;
import de.greluc.krt.iri.basetool.backend.model.OrgUnitMembershipId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link OrgUnitMembership}. Provides the few derived finders that the
 * R2.b service layer will need to answer the two recurring questions: "which org units does user X
 * belong to?" (membership listing for the active-context switcher and the owner picker) and "who
 * are the members of org unit Y?" (admin roster pages).
 *
 * <p>Uses the {@link OrgUnitMembershipId} composite key — Spring Data resolves the embeddable on
 * its own as long as the type parameter matches. The {@code findById(OrgUnitMembershipId)} method
 * inherited from {@link JpaRepository} is the canonical lookup for "does user X currently belong to
 * org unit Y?".
 */
@Repository
public interface OrgUnitMembershipRepository
    extends JpaRepository<OrgUnitMembership, OrgUnitMembershipId> {

  /**
   * Returns every membership row belonging to the given user. Used by the active-context switcher
   * (to populate the dropdown of org units the user may pin) and by the owner-picker fragment on
   * create forms (to enumerate the legal {@code owningOrgUnitId} values). Result order is insertion
   * order — callers that need a stable display order (Staffel first, SKs alphabetical, etc.) sort
   * in the service layer.
   *
   * @param userId the user whose memberships to list; never {@code null}.
   * @return every membership of this user; never {@code null}, possibly empty when the user has no
   *     org-unit membership at all (admin or guest).
   */
  List<OrgUnitMembership> findAllByIdUserId(UUID userId);

  /**
   * Counts the org-unit memberships of the given user. Used by {@code OrgUnitMembershipService} to
   * detect the two inventory-relevant boundary transitions — gaining the first membership
   * (membershipless → member) and losing the last (member → membershipless) — so {@link
   * de.greluc.krt.iri.basetool.backend.service.InventoryOrgUnitReconciler} can re-stamp the user's
   * inventory accordingly.
   *
   * @param userId the user whose memberships to count; never {@code null}.
   * @return the number of org-unit membership rows of this user (0 when membershipless).
   */
  long countByIdUserId(UUID userId);

  /**
   * Returns every membership row of the given user filtered by kind. Handy for "what Staffel does
   * this user belong to" (returns at most one row because of the V95 partial unique index) and for
   * "what Spezialkommandos has this user joined" — without forcing the caller to filter the full
   * result client-side.
   *
   * @param userId the user whose memberships to list; never {@code null}.
   * @param kind the discriminator value to match; never {@code null}.
   * @return memberships of the requested kind; never {@code null}, possibly empty.
   */
  List<OrgUnitMembership> findAllByIdUserIdAndKind(UUID userId, OrgUnitKind kind);

  /**
   * Returns the single Squadron membership of the given user, if any. Convenience wrapper around
   * {@link #findAllByIdUserIdAndKind} that takes advantage of the V95 partial unique index "{@code
   * uq_org_unit_membership_one_squadron}" — a user has at most one Staffel membership, so the
   * result is always at most one row. The implementation calls {@code findOneByIdUserIdAndKind}
   * (Spring Data derives the query); if data corruption ever produces a second Staffel membership
   * for the same user, Spring Data will surface it as an {@code
   * IncorrectResultSizeDataAccessException} which the service layer translates into a 500-level
   * problem detail.
   *
   * @param userId the user whose Staffel membership to fetch; never {@code null}.
   * @return the user's single Staffel membership if present, empty otherwise.
   */
  Optional<OrgUnitMembership> findOneByIdUserIdAndKind(UUID userId, OrgUnitKind kind);

  /**
   * Returns every membership belonging to the given org unit. Used by the admin roster page for an
   * SK ("list every member of SK ALPHA") and by the Lead-management endpoints to verify "is this
   * caller a Lead of SK ALPHA before letting them edit memberships there".
   *
   * @param orgUnitId the org unit whose members to list; never {@code null}.
   * @return memberships of this org unit; never {@code null}, possibly empty.
   */
  List<OrgUnitMembership> findAllByIdOrgUnitId(UUID orgUnitId);

  /**
   * Existence check used by the membership-management endpoints to short-circuit before issuing a
   * full SELECT: "does the caller already have a membership row in org unit X?" determines whether
   * the membership-add request is an idempotent retry or a legitimate new join.
   *
   * @param userId the user to check; never {@code null}.
   * @param orgUnitId the org unit to check; never {@code null}.
   * @return {@code true} iff a membership row already exists for this {@code (user, org_unit)}
   *     pair.
   */
  boolean existsByIdUserIdAndIdOrgUnitId(UUID userId, UUID orgUnitId);

  /**
   * Returns the distinct ids of every user who is a member of any of the given org units. Backs the
   * scoped branches of the blueprint availability overview (#364) — the pinned single org unit and
   * the non-admin oversight union — by resolving the in-scope org units to their member users.
   *
   * @param orgUnitIds the org units whose members to collect; never {@code null}. An empty
   *     collection yields an empty result.
   * @return the distinct member user ids across the given org units; never {@code null}.
   */
  @Query("SELECT DISTINCT m.id.userId FROM OrgUnitMembership m WHERE m.id.orgUnitId IN :orgUnitIds")
  Set<UUID> findDistinctUserIdsByOrgUnitIdIn(@Param("orgUnitIds") Collection<UUID> orgUnitIds);
}
