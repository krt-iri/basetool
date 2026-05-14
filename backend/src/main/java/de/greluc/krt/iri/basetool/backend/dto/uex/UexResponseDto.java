package de.greluc.krt.iri.basetool.backend.dto.uex;

import java.util.List;
import lombok.Builder;

/**
 * Generic envelope used by every UEX Corp endpoint: a literal {@code "ok"} status plus the payload
 * collection. The type parameter {@code T} is one of the inbound {@code Uex*Dto} records in this
 * package — bound at the {@code UexClient} call site to a concrete row type.
 *
 * <p>Note: a {@code @param <T>} Javadoc tag is intentionally NOT used here. Checkstyle's {@code
 * MissingJavadocMethod} machinery and CodeQL both treat records' auto-generated accessors ({@link
 * #status()}, {@link #data()}) plus the synthetic {@code build()}/{@code toString()} as methods
 * that should carry every record-level {@code @param} — which is meaningless for a type parameter
 * and surfaces as spurious "@param tag does not match any actual parameter" findings.
 */
@Builder
public record UexResponseDto<T>(String status, List<T> data) {}
