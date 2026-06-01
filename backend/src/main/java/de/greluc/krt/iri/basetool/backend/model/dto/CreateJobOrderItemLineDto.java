package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One ordered finished-item line in an item-order create payload: which {@code gameItem} to order,
 * via which {@code blueprint}, in what {@code amount}, plus the per-material quality choices. The
 * blueprint must produce the game item (validated server-side). {@code clientLineId} and {@code
 * parentClientLineId} are transient client-assigned ids used only to reconstruct sub-assembly
 * provenance at persist time — they carry no meaning after the order is saved.
 *
 * @param gameItemId the finished item to order
 * @param blueprintId the recipe chosen to produce it (must output {@code gameItemId})
 * @param amount whole-unit count to order (≥ 1)
 * @param materials per-material quality choices; omitted materials use the blueprint default
 * @param clientLineId transient client id of this line, for provenance linking; may be {@code null}
 * @param parentClientLineId transient client id of the line this was adopted from; {@code null} for
 *     a top-level line
 */
public record CreateJobOrderItemLineDto(
    @NotNull UUID gameItemId,
    @NotNull UUID blueprintId,
    @NotNull @Min(1) Integer amount,
    @Valid @Size(max = 100) List<CreateJobOrderItemMaterialDto> materials,
    @Nullable Integer clientLineId,
    @Nullable Integer parentClientLineId) {}
