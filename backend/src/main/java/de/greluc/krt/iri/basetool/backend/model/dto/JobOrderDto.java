package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.JobOrderType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data transfer record carrying Job Order payload. The same record serves both order kinds (see
 * {@code type}): a {@code MATERIAL} order populates {@code materials}; an {@code ITEM} order
 * populates {@code items} (ordered finished items) and {@code aggregatedMaterials} (the internal
 * material requirements derived from the items, grouped by material + quality). The unused list is
 * empty for the respective kind, so the detail UI can render both with one shared shell.
 *
 * @param id job order primary key
 * @param displayId human-readable sequential id
 * @param creatingSquadron author org unit (slim reference)
 * @param requestingSquadron recipient org unit (slim reference)
 * @param handle contact handle
 * @param comment optional free-text note
 * @param priority queue priority (null when terminal)
 * @param status lifecycle status
 * @param type order kind ({@code MATERIAL} or {@code ITEM})
 * @param materials material lines (populated for {@code MATERIAL} orders; empty for {@code ITEM})
 * @param items ordered finished-item lines (populated for {@code ITEM} orders; empty for {@code
 *     MATERIAL})
 * @param aggregatedMaterials derived material requirements grouped by material + quality (populated
 *     for {@code ITEM} orders; empty for {@code MATERIAL})
 * @param assignees users assigned to the order
 * @param handovers material-handover events (populated for {@code MATERIAL} orders)
 * @param itemHandovers item-handover events (populated for {@code ITEM} orders)
 * @param createdAt creation instant (UTC)
 * @param version optimistic-lock version
 */
public record JobOrderDto(
    UUID id,
    Integer displayId,
    SquadronReferenceDto creatingSquadron,
    SquadronReferenceDto requestingSquadron,
    String handle,
    String comment,
    Integer priority,
    JobOrderStatus status,
    JobOrderType type,
    List<JobOrderMaterialDto> materials,
    List<JobOrderItemDto> items,
    List<AggregatedMaterialDto> aggregatedMaterials,
    List<UserDto> assignees,
    List<JobOrderHandoverDto> handovers,
    List<JobOrderItemHandoverDto> itemHandovers,
    Instant createdAt,
    Long version) {}
