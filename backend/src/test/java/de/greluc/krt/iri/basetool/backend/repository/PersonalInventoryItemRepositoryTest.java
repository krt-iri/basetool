package de.greluc.krt.iri.basetool.backend.repository;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryItem;
import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryLocationType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersonalInventoryItemRepositoryTest {

  private static final String OWNER_A = "owner-a";
  private static final String OWNER_B = "owner-b";

  @Autowired private PersonalInventoryItemRepository repository;

  @BeforeEach
  void clean() {
    repository.deleteAll();
  }

  @Test
  void findAllByOwnerSubShouldReturnOnlyMatchingItems() {
    // Given
    repository.save(item(OWNER_A, "Medkit"));
    repository.save(item(OWNER_A, "Ammo"));
    repository.save(item(OWNER_B, "Helmet"));

    // When
    Page<PersonalInventoryItem> page =
        repository.findAllByOwnerSub(OWNER_A, PageRequest.of(0, 10, Sort.by("name")));

    // Then
    assertEquals(2, page.getTotalElements());
    assertTrue(page.getContent().stream().allMatch(i -> OWNER_A.equals(i.getOwnerSub())));
  }

  @Test
  void findByIdAndOwnerSubShouldEnforceOwnership() {
    // Given
    PersonalInventoryItem aItem = repository.save(item(OWNER_A, "Medkit"));

    // When
    Optional<PersonalInventoryItem> ownLookup =
        repository.findByIdAndOwnerSub(aItem.getId(), OWNER_A);
    Optional<PersonalInventoryItem> foreignLookup =
        repository.findByIdAndOwnerSub(aItem.getId(), OWNER_B);

    // Then
    assertTrue(ownLookup.isPresent());
    assertTrue(foreignLookup.isEmpty(), "Foreign owner must NOT be able to load this item.");
  }

  @Test
  void nameSearchShouldBeCaseInsensitiveAndOwnerScoped() {
    // Given
    repository.save(item(OWNER_A, "Medkit Alpha"));
    repository.save(item(OWNER_A, "MEDKIT BETA"));
    repository.save(item(OWNER_A, "Helmet"));
    repository.save(item(OWNER_B, "Medkit Foreign"));

    // When
    Page<PersonalInventoryItem> page =
        repository.findAllByOwnerSubAndNameContainingIgnoreCase(
            OWNER_A, "medkit", PageRequest.of(0, 10, Sort.by("name")));

    // Then
    assertEquals(
        2,
        page.getTotalElements(),
        "Search must be case-insensitive AND must not return foreign owner's items.");
  }

  @Test
  void versionShouldStartAtZeroAndIncrementOnUpdate() {
    // Given
    PersonalInventoryItem saved = repository.saveAndFlush(item(OWNER_A, "Vase"));
    Long initialVersion = saved.getVersion();
    assertNotNull(initialVersion);

    // When
    saved.setQuantity(saved.getQuantity() + 1);
    PersonalInventoryItem updated = repository.saveAndFlush(saved);

    // Then
    assertNotNull(updated.getVersion());
    assertTrue(
        updated.getVersion() > initialVersion,
        "JPA must increment @Version on each update – this is the basis for the 409 contract.");
  }

  private static PersonalInventoryItem item(String ownerSub, String name) {
    return PersonalInventoryItem.builder()
        .ownerSub(ownerSub)
        .name(name)
        .note(null)
        .locationUexId(1)
        .locationType(PersonalInventoryLocationType.CITY)
        .locationNameSnapshot("Lorville")
        .quantity(1)
        .build();
  }
}
