package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.PingResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SystemController}. Pure-method tests against the
 * controller instance — the `@PreAuthorize("permitAll()")` and the
 * `@ApiDeprecation` headers are exercised by the existing
 * {@code ApiDeprecationTest} and security tests; here we only assert the
 * value the methods return.
 *
 * <p>The contract under test:
 * <ul>
 *   <li>v1 ping returns the legacy {@code Map<String,String>} body with
 *       {@code status=UP / version=v1 / message=pong} — verbatim, because
 *       external monitors may still rely on it (Deprecation/Sunset headers
 *       give callers until {@code 2026-12-31} to migrate).</li>
 *   <li>v2 ping returns a {@link PingResponse} record with the same status
 *       triple plus a fresh UTC {@code Instant} timestamp — the addition is
 *       what motivated the v2 carve-out (see CLAUDE.md "All times in UTC").</li>
 * </ul>
 */
class SystemControllerTest {

    private final SystemController controller = new SystemController();

    @Test
    void pingV1_returnsLegacyMapBodyVerbatim() {
        Map<String, String> body = controller.pingV1();

        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("v1", body.get("version"));
        assertEquals("pong", body.get("message"));
        assertEquals(3, body.size(), "v1 ping must NOT add new fields — the v2 carve-out exists for that");
    }

    @Test
    void pingV2_returnsPingResponseRecordWithUtcTimestamp() {
        Instant before = Instant.now();
        PingResponse response = controller.pingV2();
        Instant after = Instant.now();

        assertNotNull(response);
        assertEquals("UP", response.status());
        assertEquals("v2", response.version());
        assertEquals("pong", response.message());

        // Timestamp must be a fresh UTC Instant (no timezone arithmetic) —
        // verifies the CLAUDE.md "All times in UTC" rule at the boundary.
        assertNotNull(response.timestamp());
        assertFalse(response.timestamp().isBefore(before),
                "Timestamp must be at-or-after the call started");
        assertFalse(response.timestamp().isAfter(after),
                "Timestamp must be at-or-before the call returned");
    }
}
