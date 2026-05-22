package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.dto.SpecialCommandDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between {@link SpecialCommand} entities and {@link SpecialCommandDto} wire
 * shapes. Mirrors the {@link SquadronMapper} contract — fields are 1:1 except for the absent
 * {@code isPromotionEnabled} field on the wire shape (permanently disabled for SK rows by the V94
 * CHECK constraint and the {@link SpecialCommand} setter override, so exposing the flag would only
 * confuse callers).
 */
@Mapper(componentModel = "spring")
public interface SpecialCommandMapper {

  /**
   * Maps a {@link SpecialCommand} entity to its outbound DTO. Audit fields and the (always-false)
   * {@code isPromotionEnabled} accessor are intentionally not surfaced on the wire.
   *
   * @param entity the persisted entity; never {@code null} in the live call paths.
   * @return the wire-shape DTO, never {@code null}.
   */
  SpecialCommandDto toDto(SpecialCommand entity);

  /**
   * Builds a new {@link SpecialCommand} entity from the inbound DTO. The {@code @Mapping(ignore =
   * true)} declarations cover the server-managed fields (id is server-stamped on create; audit
   * timestamps are populated by Hibernate's {@code @CreationTimestamp} / {@code @UpdateTimestamp}).
   * {@code kind} is set automatically by the JPA discriminator on persist — every entity built
   * through this path lands as {@code kind='SPECIAL_COMMAND'}.
   *
   * @param dto the inbound DTO; never {@code null}.
   * @return a transient {@link SpecialCommand} instance ready to be saved.
   */
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  SpecialCommand toEntity(SpecialCommandDto dto);
}
