package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, List<String> sort) {}
