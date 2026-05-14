package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Outbound write DTO for updating an existing personal inventory entry. The {@code version} field
 * is mandatory on the backend (optimistic locking).
 */
public record PersonalInventoryItemUpdateRequest(
    String name,
    String note,
    Integer locationUexId,
    PersonalInventoryLocationType locationType,
    Integer quantity,
    Long version) {}
