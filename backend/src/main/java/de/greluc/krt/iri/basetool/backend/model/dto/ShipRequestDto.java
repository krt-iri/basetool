package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Ship Request payload (create + update).
 *
 * <p>R5.d.f added the trailing {@link #owningOrgUnitId} picker output. When present on create, the
 * service layer routes the stamping through {@code
 * OwnerScopeService.resolveSquadronForPickerOutput} — the picked OrgUnit is validated against the
 * target user's memberships and Spezialkommando selections are refused with 400 until the
 * destructive cleanup release loosens NOT NULL on the legacy {@code owning_squadron_id} column.
 * When {@code null}, the service falls back to the target user's home Staffel ({@code
 * User.getSquadron()}), preserving the legacy stamping path for the single-membership case that
 * covers 100 % of users today. Ignored on update — the existing stamp survives.
 */
public record ShipRequestDto(
    String name,
    @NotNull(message = "{validation.shiptype.required}") UUID shipTypeId,
    @NotBlank(message = "{validation.insurance.required}")
        @Pattern(
            regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$",
            message = "{validation.insurance.pattern}")
        String insurance,
    UUID locationId,
    boolean fitted,
    Long version,
    @Nullable UUID owningOrgUnitId) {}
