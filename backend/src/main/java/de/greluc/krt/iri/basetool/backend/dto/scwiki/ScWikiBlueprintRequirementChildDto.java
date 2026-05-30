package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * One concrete ingredient slot inside a {@link ScWikiBlueprintRequirementGroupDto} (SC Wiki {@code
 * blueprint_requirement_child}). Unlike the flat {@code ingredients[]} summary, a child carries the
 * per-group reference and the {@link #minQuality} gate. {@link #kind} selects the reference type:
 *
 * <ul>
 *   <li>{@code "resource"} → {@link #uuid} is the commodity's resource-type UUID (resolves to a
 *       {@code material}), {@link #quantityScu} carries the SCU amount;
 *   <li>{@code "item"} → {@link #uuid} is the game-item UUID (resolves to a {@code game_item}),
 *       {@link #quantity} carries the whole-unit count.
 * </ul>
 *
 * @param key internal key of the child, or {@code null}
 * @param kind {@code "resource"} or {@code "item"} (case-insensitive), or {@code null}
 * @param uuid resource-type UUID (resource) or item UUID (item), or {@code null}
 * @param name display name of the required resource / item, or {@code null}
 * @param quantity discrete unit count required for an ITEM child, or {@code null}
 * @param quantityScu SCU amount required for a RESOURCE child, or {@code null}
 * @param minQuality minimum quality tier required, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintRequirementChildDto(
    @JsonProperty("key") String key,
    @JsonProperty("kind") String kind,
    @JsonProperty("uuid") UUID uuid,
    @JsonProperty("name") String name,
    @JsonProperty("quantity") Integer quantity,
    @JsonProperty("quantity_scu") Double quantityScu,
    @JsonProperty("min_quality") Integer minQuality) {}
