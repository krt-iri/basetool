package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import de.greluc.krt.iri.basetool.backend.model.dto.SpecialCommandDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper between {@link SpecialCommand} entities and {@link SpecialCommandDto} wire
 * shapes. Mirrors the {@link SquadronMapper} contract — fields are 1:1 except for the absent {@code
 * isPromotionEnabled} field on the wire shape (permanently disabled for SK rows by the V94 CHECK
 * constraint and the {@link SpecialCommand} setter override, so exposing the flag would only
 * confuse callers).
 */
@Mapper(componentModel = "spring")
public interface SpecialCommandMapper {

  /**
   * Maps a {@link SpecialCommand} entity to its outbound DTO. Audit fields and the (always-false)
   * {@code isPromotionEnabled} accessor are intentionally not surfaced on the wire. The {@code
   * isProfitEligible} flag IS surfaced — unlike promotion, it is a real per-SK value an admin
   * toggles to mark which Spezialkommandos may process orders, so the admin-settings page renders
   * the toggle from this value.
   *
   * @param entity the persisted entity; never {@code null} in the live call paths.
   * @return the wire-shape DTO, never {@code null}.
   */
  @Mapping(target = "isProfitEligible", source = "profitEligible")
  SpecialCommandDto toDto(SpecialCommand entity);

  /**
   * Builds a new {@link SpecialCommand} entity from the inbound DTO. The {@code @Mapping(ignore =
   * true)} declarations cover the server-managed fields (id is server-stamped on create; audit
   * timestamps are populated by Hibernate's {@code @CreationTimestamp} / {@code @UpdateTimestamp}).
   * {@code kind} is set automatically by the JPA discriminator on persist — every entity built
   * through this path lands as {@code kind='SPECIAL_COMMAND'}. {@code promotionEnabled} is ignored
   * because it is permanently {@code false} on SK rows (constructor + setter override). {@code
   * profitEligible} is ignored because, like the squadron flag, it is mutated only through the
   * dedicated {@code PATCH /api/v1/special-commands/{id}/profit-eligible} toggle, never as a
   * side-effect of a create/update, so an accidental edit cannot change which SKs may process
   * orders.
   *
   * @param dto the inbound DTO; never {@code null}.
   * @return a transient {@link SpecialCommand} instance ready to be saved.
   */
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "promotionEnabled", ignore = true)
  @Mapping(target = "profitEligible", ignore = true)
  SpecialCommand toEntity(SpecialCommandDto dto);
}
