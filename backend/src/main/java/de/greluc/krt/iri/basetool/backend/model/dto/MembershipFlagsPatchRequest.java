package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patch payload for the per-membership role flags on {@link
 * de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership}. Endpoint {@code PATCH
 * /api/v1/special-commands/{id}/members/{userId}} accepts this record so the admin / SK-Lead UI can
 * flip one or both flags in a single round-trip without touching the others.
 *
 * <p>Each field is a boxed {@link Boolean}: a {@code null} value on the wire means "leave the
 * current row value alone", a non-null value writes that exact value to the matching column. The
 * standard PATCH semantics — clients only send the fields they want to change.
 *
 * <p>The {@link #version} field is required so concurrent admin edits surface as a 409 instead of
 * silently overwriting each other. {@code is_lead} is intentionally NOT part of this payload — Lead
 * toggles ship through a dedicated ADMIN-only endpoint ({@code PATCH .../lead}) so the audit trail
 * can clearly attribute "X promoted Y to Lead of SK Z" to a specific admin action.
 *
 * @param isLogistician new value of the Logistician flag, or {@code null} to leave unchanged.
 * @param isMissionManager new value of the Mission Manager flag, or {@code null} to leave
 *     unchanged.
 * @param version current optimistic-lock version held by the client; required.
 */
public record MembershipFlagsPatchRequest(
    @Nullable Boolean isLogistician, @Nullable Boolean isMissionManager, @NotNull Long version) {}
