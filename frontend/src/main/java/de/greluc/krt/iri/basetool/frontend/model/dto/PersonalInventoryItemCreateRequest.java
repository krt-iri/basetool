package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Outbound write DTO for creating a personal inventory entry. Validation is enforced on the
 * frontend form ({@link de.greluc.krt.iri.basetool.frontend.model.form.PersonalInventoryForm});
 * this record is just the wire shape sent to the backend, so re-declaring constraints here would
 * only duplicate the backend-side validation that ultimately decides acceptance.
 */
public record PersonalInventoryItemCreateRequest(
    String name,
    String note,
    Integer locationUexId,
    PersonalInventoryLocationType locationType,
    Integer quantity) {}
