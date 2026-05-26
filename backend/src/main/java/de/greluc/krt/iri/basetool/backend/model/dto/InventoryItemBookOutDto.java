package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.CheckoutType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Inventory Item Book Out payload.
 *
 * <p>R5.d.g added the trailing {@link #targetOwningOrgUnitId} picker output. Applies only to the
 * {@link CheckoutType#TRANSFER} branch — the cross-user transfer flow lands the new {@link
 * de.greluc.krt.iri.basetool.backend.model.InventoryItem} row on the picked org unit instead of the
 * destination user's home Staffel. The service routes the stamp through {@code
 * OwnerScopeService.resolveSquadronForPickerOutput(targetUser, targetOwningOrgUnitId)} — the
 * resolver validates the picked OrgUnit against the *destination* user's memberships (intentional
 * cross-org-unit semantics per plan §D4: User A from Staffel-X may book out into User B's
 * Spezialkommando-Y stock as long as User B is a member of Y). Spezialkommando selections are still
 * refused with 400 until the destructive cleanup release loosens NOT NULL on the legacy {@code
 * owning_squadron_id} column.
 *
 * <p>Ignored for {@link CheckoutType#CONSUME} and {@link CheckoutType#SELL} — both terminate the
 * inventory row and never create a new ownership stamp.
 */
public record InventoryItemBookOutDto(
    @NotNull @Min(0) Double amount,
    UUID targetUserId,
    UUID targetLocationId,
    CheckoutType type,
    String terminal,
    @Min(0) BigDecimal sellAmount,
    @NotNull Long version,
    @Nullable UUID targetOwningOrgUnitId) {}
