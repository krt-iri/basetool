package de.greluc.krt.iri.basetool.backend.dto.uex;

import java.util.List;
import lombok.Builder;

@Builder
public record UexResponseDto<T>(String status, List<T> data) {}
