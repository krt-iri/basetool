package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementDto(
        UUID id,
        String content,
        Instant updatedAt,
        Long version
) {}
