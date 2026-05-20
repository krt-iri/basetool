package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Data transfer record carrying Mission Finance Entry Update payload. Caps mirror the create
 * payload (audit finding C-2) so the post-create edit path cannot smuggle in payloads that would
 * have been rejected at create time.
 */
public record MissionFinanceEntryUpdateDto(
    @Size(max = 2000) String note,
    @NotNull FinanceType type,
    @NotNull @DecimalMin("0.0") @DecimalMax("1000000000.0") BigDecimal amount,
    @NotNull Long version) {}
