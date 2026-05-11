package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.annotation.ApiDeprecation;
import de.greluc.krt.iri.basetool.backend.model.dto.PingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;


@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "System and API versioning endpoints")
public class SystemController {

    @GetMapping("/v1/system/ping")
    @PreAuthorize("permitAll()")
    @Operation(summary = "System Ping (Legacy)", description = "Deprecated. Use /api/v2/system/ping instead.")
    @ApiDeprecation(sunset = "2026-12-31", replacement = "/api/v2/system/ping")
    @ApiResponse(responseCode = "200", description = "Ping response",
            content = @Content(mediaType = "application/json"))
    public Map<String, String> pingV1() {
        return Map.of("status", "UP", "version", "v1", "message", "pong");
    }

    @GetMapping("/v2/system/ping")
    @PreAuthorize("permitAll()")
    @Operation(summary = "System Ping (Current)", description = "Returns system status with UTC timestamp.")
    @ApiResponse(responseCode = "200", description = "Ping response with timestamp",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PingResponse.class)))
    public PingResponse pingV2() {
        return new PingResponse("UP", "v2", "pong", Instant.now());
    }
}
