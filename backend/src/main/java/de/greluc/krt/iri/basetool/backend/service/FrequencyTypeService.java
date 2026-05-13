package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.repository.FrequencyTypeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD plus drag-and-drop reorder for the {@code frequency_type} reference table.
 *
 * <p>Frequency types model the radio-channel categories used on a mission. Soft-delete via {@code
 * active=false}. The reorder endpoint persists a new {@code sort_index} per id; the admin UI uses
 * drag-and-drop and posts the full new ordering.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FrequencyTypeService {

  private final FrequencyTypeRepository frequencyTypeRepository;

  /**
   * @param active optional active-filter; null returns both active and inactive
   * @param pageable page request
   * @return paged frequency type list
   */
  public Page<FrequencyType> getAllFrequencyTypes(Boolean active, @NotNull Pageable pageable) {
    return frequencyTypeRepository.findAllByActive(active, pageable);
  }

  /**
   * @param id frequency type primary key
   * @return the frequency type
   * @throws NotFoundException when no match
   */
  public FrequencyType getFrequencyType(@NotNull UUID id) {
    return frequencyTypeRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("FrequencyType not found"));
  }

  /**
   * Persists a new frequency type. The backend assigns the next sort index automatically.
   *
   * @param frequencyType transient entity
   * @return the persisted frequency type
   */
  @Transactional
  public FrequencyType createFrequencyType(@NotNull FrequencyType frequencyType) {
    return frequencyTypeRepository.save(frequencyType);
  }

  /**
   * Updates name, description and active flag. The {@code sort_index} is preserved — use {@link
   * #reorderFrequencyTypes} to change the order.
   *
   * @param id frequency type primary key
   * @param frequencyType transient entity carrying the new values
   * @return the persisted frequency type
   * @throws NotFoundException when no match
   */
  @Transactional
  public FrequencyType updateFrequencyType(@NotNull UUID id, @NotNull FrequencyType frequencyType) {
    FrequencyType existing = getFrequencyType(id);
    existing.setName(frequencyType.getName());
    existing.setDescription(frequencyType.getDescription());
    existing.setActive(frequencyType.isActive());
    return frequencyTypeRepository.save(existing);
  }

  /**
   * Soft-deletes a frequency type by flipping {@code active=false}.
   *
   * @param id frequency type primary key
   * @throws NotFoundException when no match
   */
  @Transactional
  public void deleteFrequencyType(@NotNull UUID id) {
    if (!frequencyTypeRepository.existsById(id)) {
      throw new NotFoundException("FrequencyType not found");
    }
    FrequencyType existing = getFrequencyType(id);
    existing.setActive(false);
    frequencyTypeRepository.save(existing);
  }

  /**
   * Reverses a soft-delete by flipping {@code active=true}.
   *
   * @param id frequency type primary key
   * @throws NotFoundException when no match
   */
  @Transactional
  public void activateFrequencyType(@NotNull UUID id) {
    FrequencyType existing = getFrequencyType(id);
    existing.setActive(true);
    frequencyTypeRepository.save(existing);
  }

  /**
   * Persists a new ordering of frequency types. The position of each id in the supplied list is
   * written as the row's new {@code sort_index}. Missing ids are silently skipped (defensive — the
   * admin UI submits the full current list).
   *
   * @param ids ids in the desired new order
   */
  @Transactional
  public void reorderFrequencyTypes(@NotNull List<UUID> ids) {
    for (int i = 0; i < ids.size(); i++) {
      int index = i;
      frequencyTypeRepository
          .findById(ids.get(i))
          .ifPresent(
              freq -> {
                freq.setSortIndex(index);
                frequencyTypeRepository.save(freq);
              });
    }
  }
}
