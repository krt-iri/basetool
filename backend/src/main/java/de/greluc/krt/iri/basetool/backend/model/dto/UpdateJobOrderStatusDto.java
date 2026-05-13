package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating the status of a JobOrder. Includes the version field for optimistic locking to
 * prevent lost updates in concurrent scenarios.
 */
public record UpdateJobOrderStatusDto(@NotNull JobOrderStatus status, @NotNull Long version) {}
