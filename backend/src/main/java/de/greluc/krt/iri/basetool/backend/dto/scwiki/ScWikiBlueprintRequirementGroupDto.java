package de.greluc.krt.iri.basetool.backend.dto.scwiki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One named build slot of a blueprint (SC Wiki {@code blueprint_requirement_group}), e.g. {@code
 * FRAME} / {@code EMITTER}. It bundles the ingredient(s) that fill the slot ({@link #children})
 * with the stat contributions that slot makes to the crafted item ({@link #modifiers}).
 *
 * <p>Present only on the blueprint <em>detail</em> response ({@code GET /api/blueprints/{uuid}}),
 * never on the list endpoint — the {@code ScWikiBlueprintSyncService} therefore fetches each
 * blueprint's detail to capture the modifier (stat) data.
 *
 * @param key internal group key (e.g. {@code "EMITTER"}), or {@code null}
 * @param name display name of the slot (e.g. {@code "Emitter"}), or {@code null}
 * @param kind always {@code "group"} for a requirement group, or {@code null}
 * @param requiredCount number of children that must be fulfilled within the group, or {@code null}
 * @param modifiers the stat contributions of this slot, or {@code null} when the slot is inert
 * @param children the ingredient(s) that fill this slot, or {@code null}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScWikiBlueprintRequirementGroupDto(
    @JsonProperty("key") String key,
    @JsonProperty("name") String name,
    @JsonProperty("kind") String kind,
    @JsonProperty("required_count") Integer requiredCount,
    @JsonProperty("modifiers") List<ScWikiBlueprintModifierDto> modifiers,
    @JsonProperty("children") List<ScWikiBlueprintRequirementChildDto> children) {}
