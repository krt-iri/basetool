package de.greluc.krt.iri.basetool.backend.dto.uex;

import lombok.Builder;

import java.util.List;

@Builder
public record UexResponseDto<T>(
        String status,
        List<T> data
) {}
