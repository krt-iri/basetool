package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding object for Spezialkommando input on the {@code /admin/special-commands} create /
 * update modal. Mirrors {@link SquadronForm} field-for-field — the SK admin surface has no
 * Spezialkommando-specific fields beyond what the parent {@code OrgUnit} carries.
 *
 * @param name display name; required, max 255 chars.
 * @param shorthand short tag used on chips / badges; required, max 50 chars.
 * @param description free-form text; max 1000 chars (matches the backend column).
 * @param version optimistic-lock counter; required on update, ignored on create.
 */
public record SpecialCommandForm(
    @NotBlank(message = "{validation.name.required}") @Size(max = 255) String name,
    @NotBlank(message = "{validation.shorthand.required}") @Size(max = 50) String shorthand,
    @Size(max = 1000) String description,
    Long version) {}
