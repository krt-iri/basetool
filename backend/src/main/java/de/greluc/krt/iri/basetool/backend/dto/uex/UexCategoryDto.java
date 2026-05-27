package de.greluc.krt.iri.basetool.backend.dto.uex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inbound JSON record for UEX Corp's {@code /categories} endpoint. Mapped to the project's own
 * {@code UexCategory} reference entity by {@code UexCategoryRefService}; downstream code consumes
 * the entity, not this DTO.
 *
 * <p>{@code type} is one of {@code "item"} or {@code "vehicle"} — used by {@code
 * UexItemSyncService} to know whether an entry under this category is bound for the {@code
 * game_item} table (items) or the {@code ship_type} table (vehicles).
 *
 * @param id UEX integer category id (1..98+); stable across runs
 * @param type {@code "item"} or {@code "vehicle"}
 * @param section coarse grouping, e.g. {@code "Armor"}, {@code "Vehicle Weapons"}, {@code
 *     "Systems"}
 * @param name subcategory display name, e.g. {@code "Helmets"}, {@code "Torso"}
 * @param isGameRelated UEX integer flag (0/1); driving the inner-loop filter
 * @param isMining UEX integer flag (0/1)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UexCategoryDto(
    @JsonProperty("id") Integer id,
    @JsonProperty("type") String type,
    @JsonProperty("section") String section,
    @JsonProperty("name") String name,
    @JsonProperty("is_game_related") Integer isGameRelated,
    @JsonProperty("is_mining") Integer isMining) {}
