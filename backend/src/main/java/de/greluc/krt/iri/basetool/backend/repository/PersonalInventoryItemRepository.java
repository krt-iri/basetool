package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PersonalInventoryItem}. All non-admin lookups MUST use one of
 * the {@code *ByOwnerSub*} variants in order to enforce the multi-user data isolation rule (see
 * AGENTS.md "MULTI-USER DATA ISOLATION").
 */
@Repository
public interface PersonalInventoryItemRepository
    extends JpaRepository<PersonalInventoryItem, UUID> {

  Page<PersonalInventoryItem> findAllByOwnerSub(String ownerSub, Pageable pageable);

  Page<PersonalInventoryItem> findAllByOwnerSubAndNameContainingIgnoreCase(
      String ownerSub, String nameFragment, Pageable pageable);

  Optional<PersonalInventoryItem> findByIdAndOwnerSub(UUID id, String ownerSub);
}
