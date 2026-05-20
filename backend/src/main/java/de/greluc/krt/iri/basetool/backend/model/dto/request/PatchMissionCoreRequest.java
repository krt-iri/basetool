package de.greluc.krt.iri.basetool.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Request DTO for a partial update of the master-data (core) section of a mission.
 *
 * <p>The {@code version} field is the dedicated {@code mission.core_version} section counter — not
 * the global {@code Mission.@Version}. This is what enables concurrent users to edit the schedule
 * or flags section in parallel without their saves invalidating this form (or vice versa).
 *
 * <p>{@code operationId} is part of the core section: changing the parent operation does not bump
 * the schedule or flags counters and only requires a fresh {@code version} value here.
 *
 * <p>{@code calendarLink} is rendered as an {@code &lt;a href&gt;} on the public landing page (see
 * {@code index.html}). The {@code @Pattern} forces an {@code https://} prefix so a mission manager
 * cannot persist a {@code javascript:fetch(document.cookie)} stored-XSS payload — Thymeleaf's
 * {@code th:href} only HTML-escapes the value, it does not scheme-filter. Audit finding H-1.
 */
public record PatchMissionCoreRequest(
    @NotBlank @Size(max = 255) String name,
    @Nullable @Size(max = 10000) String description,
    @Nullable
        @Size(max = 2048)
        @Pattern(regexp = "^(https://.*)?$", message = "must start with https://")
        String calendarLink,
    @Nullable @Size(max = 64) String status,
    @Nullable UUID operationId,
    @NotNull Long version) {}
