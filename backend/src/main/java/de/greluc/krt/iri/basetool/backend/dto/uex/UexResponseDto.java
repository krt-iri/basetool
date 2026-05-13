package de.greluc.krt.iri.basetool.backend.dto.uex;

import java.util.List;
import lombok.Builder;

/**
 * Generic envelope used by every UEX Corp endpoint: a literal {@code "ok"} status plus the payload
 * collection. The {@code T} parameter is one of the inbound {@code Uex*Dto} records in this
 * package.
 *
 * @param <T> the row type for this UEX endpoint
 */
@Builder
public record UexResponseDto<T>(String status, List<T> data) {}
