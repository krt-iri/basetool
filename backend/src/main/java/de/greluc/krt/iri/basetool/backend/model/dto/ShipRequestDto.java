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
 * service layer routes the stamping through {@code OwnerScopeService.resolveOrgUnitForPickerOutput}
 * — the picked OrgUnit is validated against the target user's memberships in {@code
 * org_unit_membership}. When {@code null}, the resolver auto-stamps the user's single membership
 * (today's common single-Staffel case). Ignored on update — the existing stamp survives.
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
