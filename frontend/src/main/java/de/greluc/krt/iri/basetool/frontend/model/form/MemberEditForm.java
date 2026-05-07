package de.greluc.krt.iri.basetool.frontend.model.form;

import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record MemberEditForm(
    Integer rank,
    @Size(max = 2000, message = "{validation.description.max}") String description,
    @Size(max = 255, message = "{validation.displayname.max}") String displayName,
    Long version,
    String source,
    @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDate
) {}
