package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for changing the owner of a mission via the dedicated {@code MissionOwnership}
 * aggregate. The {@code version} must match the current {@code MissionOwnership.version} (NOT the
 * parent {@code Mission.version}) to prevent lost updates on concurrent owner changes.
 */
public record UpdateMissionOwnerRequest(
        @NotNull UUID userId,
        @NotNull Long version
) {}
