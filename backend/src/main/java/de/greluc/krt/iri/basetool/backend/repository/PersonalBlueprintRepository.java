package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonalBlueprint}. All non-admin lookups MUST use one of the
 * {@code *ByOwnerSub*} variants to enforce the multi-user data isolation rule: a user only ever
 * sees blueprints they own.
 */
@Repository
public interface PersonalBlueprintRepository extends JpaRepository<PersonalBlueprint, UUID> {

  /**
   * Page of the blueprints owned by one user.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param pageable page request with a whitelisted sort
   * @return the owner's blueprints
   */
  Page<PersonalBlueprint> findAllByOwnerSub(String ownerSub, Pageable pageable);

  /**
   * Page of the blueprints owned by one user whose product name contains the given fragment
   * (case-insensitive) — backs the owned-list filter box.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param nameFragment case-insensitive product-name substring
   * @param pageable page request with a whitelisted sort
   * @return the owner's matching blueprints
   */
  Page<PersonalBlueprint> findAllByOwnerSubAndProductNameContainingIgnoreCase(
      String ownerSub, String nameFragment, Pageable pageable);

  /**
   * Owner-scoped single lookup for detail / update / delete; returns empty for a foreign or unknown
   * id so the service can answer 404 without leaking another user's ownership.
   *
   * @param id the entry id
   * @param ownerSub Keycloak {@code sub} of the owner
   * @return the entry if it belongs to the owner, empty otherwise
   */
  Optional<PersonalBlueprint> findByIdAndOwnerSub(UUID id, String ownerSub);

  /**
   * Owner-scoped product lookup; used by add / import to detect an existing ownership row before
   * the {@code (owner_sub, product_key)} unique constraint fires.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKey normalized product key
   * @return the entry if the owner already owns the product, empty otherwise
   */
  Optional<PersonalBlueprint> findByOwnerSubAndProductKey(String ownerSub, String productKey);

  /**
   * Fast owner-scoped existence check for the product, used to short-circuit duplicate adds with a
   * 409 before hitting the unique constraint.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKey normalized product key
   * @return {@code true} if the owner already owns the product
   */
  boolean existsByOwnerSubAndProductKey(String ownerSub, String productKey);

  /**
   * Owner-scoped bulk product lookup, used to compute the "already owned" flag for a page of search
   * results and to dedupe a batch add / import in a single query.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param productKeys the product keys to test
   * @return the owner's entries whose product key is in the given set
   */
  List<PersonalBlueprint> findAllByOwnerSubAndProductKeyIn(
      String ownerSub, Collection<String> productKeys);
}
