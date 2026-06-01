package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintBatchCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintBatchResult;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintUpdateRequest;
import de.greluc.krt.iri.basetool.backend.service.PersonalBlueprintService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the user-facing personal-blueprint set (#327). Every method derives the owner
 * from the JWT {@code sub} and never accepts it from the request, enforcing per-user data
 * isolation.
 */
@RestController
@RequestMapping("/api/v1/personal-blueprints")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Personal Blueprints", description = "Per-user owned crafting blueprints (#327).")
@SecurityRequirement(name = "bearerAuth")
public class PersonalBlueprintController {

  private final PersonalBlueprintService service;

  /**
   * Lists the caller's owned blueprints (paginated, sortable, optional product-name filter).
   *
   * @param page optional zero-based page index
   * @param size optional page size
   * @param sort optional sort expression over the whitelist
   * @param q optional case-insensitive product-name filter
   * @param authentication the caller's JWT authentication
   * @return paged response DTOs
   */
  @GetMapping
  @Operation(summary = "List the caller's owned blueprints (paginated, sortable, name filter).")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated list of the caller's blueprints."),
    @ApiResponse(responseCode = "401", description = "Authentication required.")
  })
  public PageResponse<PersonalBlueprintResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String q,
      JwtAuthenticationToken authentication) {
    String ownerSub = requireSub(authentication);
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page,
            size,
            sort,
            PersonalBlueprintService.SORTABLE_FIELDS,
            PersonalBlueprintService.DEFAULT_SORT_FIELD);
    Page<PersonalBlueprintResponse> result = service.listOwn(ownerSub, q, pageable);
    return new PageResponse<>(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        PaginationUtil.toSortStrings(result.getSort()));
  }

  /**
   * Adds a single blueprint to the caller's owned set.
   *
   * @param request the add payload
   * @param auth the caller's JWT authentication
   * @return the persisted DTO
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a blueprint to the caller's owned set.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Blueprint added."),
    @ApiResponse(responseCode = "400", description = "Validation failed."),
    @ApiResponse(responseCode = "404", description = "Product key matches no active product."),
    @ApiResponse(responseCode = "409", description = "Blueprint already owned.")
  })
  public PersonalBlueprintResponse add(
      @Valid @RequestBody PersonalBlueprintCreateRequest request, JwtAuthenticationToken auth) {
    return service.add(requireSub(auth), request);
  }

  /**
   * Adds several blueprints in one call (multi-select). Already-owned / unresolvable keys are
   * skipped, not rejected; the response summarizes the outcome.
   *
   * @param request the batch of product keys
   * @param auth the caller's JWT authentication
   * @return a summary of added vs. skipped keys
   */
  @PostMapping("/batch")
  @Operation(summary = "Add several blueprints at once (multi-select); skips owned/unknown keys.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batch processed; see the summary."),
    @ApiResponse(responseCode = "400", description = "Validation failed.")
  })
  public PersonalBlueprintBatchResult addBatch(
      @Valid @RequestBody PersonalBlueprintBatchCreateRequest request,
      JwtAuthenticationToken auth) {
    return service.addBatch(requireSub(auth), request.productKeys());
  }

  /**
   * Updates an owned blueprint's acquisition date / note.
   *
   * @param id entry id
   * @param request the update payload (carries the expected version)
   * @param auth the caller's JWT authentication
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @Operation(summary = "Update an owned blueprint's acquisition date / note.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Blueprint updated."),
    @ApiResponse(responseCode = "400", description = "Validation failed."),
    @ApiResponse(responseCode = "404", description = "Not found or not owned by caller."),
    @ApiResponse(responseCode = "409", description = "Optimistic lock conflict.")
  })
  public PersonalBlueprintResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody PersonalBlueprintUpdateRequest request,
      JwtAuthenticationToken auth) {
    return service.update(requireSub(auth), id, request);
  }

  /**
   * Removes a blueprint from the caller's owned set.
   *
   * @param id entry id
   * @param auth the caller's JWT authentication
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a blueprint from the caller's owned set.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Blueprint removed."),
    @ApiResponse(responseCode = "404", description = "Not found or not owned by caller.")
  })
  public void delete(@PathVariable UUID id, JwtAuthenticationToken auth) {
    service.delete(requireSub(auth), id);
  }

  /**
   * Extracts the non-blank JWT {@code sub} from the caller's authentication.
   *
   * @param auth the caller's JWT authentication
   * @return the subject claim
   * @throws AccessDeniedException if the token or its subject claim is missing
   */
  @NotNull
  private static String requireSub(JwtAuthenticationToken auth) {
    if (auth == null || auth.getToken() == null) {
      throw new AccessDeniedException("Missing JWT.");
    }
    Jwt jwt = auth.getToken();
    String sub = jwt.getSubject();
    if (sub == null || sub.isBlank()) {
      throw new AccessDeniedException("JWT does not contain a subject claim.");
    }
    return sub;
  }
}
