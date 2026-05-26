package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.UUID;

/**
 * Form-binding object for Operation input.
 *
 * <p>R5.d.e added the trailing {@link #owningOrgUnitId} field — the owner-picker output when the
 * caller belongs to more than one OrgUnit. {@code null} (single-membership case or update flow)
 * preserves the legacy "stamp from active scope" behaviour on the backend.
 */
public record OperationForm(
    String name, String description, String status, Long version, UUID owningOrgUnitId) {}
