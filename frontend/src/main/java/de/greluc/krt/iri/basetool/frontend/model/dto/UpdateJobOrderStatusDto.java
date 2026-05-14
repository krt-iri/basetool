package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * DTO for updating the status of a JobOrder via the backend API. Includes the version field for
 * optimistic locking.
 */
public record UpdateJobOrderStatusDto(String status, Long version) {}
