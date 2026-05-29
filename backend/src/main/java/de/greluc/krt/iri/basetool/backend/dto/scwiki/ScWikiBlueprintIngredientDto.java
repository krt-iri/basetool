package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * One ingredient line inside a {@link ScWikiBlueprintDto} (SC_WIKI_SYNC_PLAN.md §3.3). The {@code
 * kind} discriminator selects which reference / quantity field is populated:
 *
 * <ul>
 *   <li>{@code "resource"} → {@link #resourceTypeUuid} + {@link #quantityScu}
 *   <li>{@code "item"} → {@link #itemUuid} + {@link #quantity}
 * </ul>
 *
 * <p>Per §3.4 #3, {@link #resourceTypeUuid} is the stable cross-sync key for a RESOURCE line —
 * trust it over any embedded {@code link.uuid}.
 *
 * @param name display name of the ingredient
 * @param kind {@code "resource"} or {@code "item"} (case-insensitive)
 * @param resourceTypeUuid commodity UUID for a RESOURCE line, else {@code null}
 * @param itemUuid game-item UUID for an ITEM line, else {@code null}
 * @param quantityScu quantity in SCU for a RESOURCE line, else {@code null}
 * @param quantity quantity in whole units for an ITEM line, else {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintIngredientDto(
    @JsonProperty("name") String name,
    @JsonProperty("kind") String kind,
    @JsonProperty("resource_type_uuid") UUID resourceTypeUuid,
    @JsonProperty("item_uuid") UUID itemUuid,
    @JsonProperty("quantity_scu") Double quantityScu,
    @JsonProperty("quantity") Integer quantity) {}
