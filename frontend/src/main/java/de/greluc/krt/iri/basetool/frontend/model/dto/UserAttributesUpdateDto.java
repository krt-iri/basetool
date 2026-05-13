package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.LocalDate;
import org.jetbrains.annotations.Nullable;

/** Data transfer record carrying User Attributes Update payload. */
public record UserAttributesUpdateDto(
    Integer rank,
    String description,
    String displayName,
    Long version,
    @Nullable LocalDate joinDate) {}
