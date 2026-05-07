package de.greluc.krt.iri.basetool.frontend.model.dto;

import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;

public record UserAttributesUpdateDto(
        Integer rank,
        String description,
        String displayName,
        Long version,
        @Nullable LocalDate joinDate
) {}
