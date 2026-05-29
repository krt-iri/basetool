package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.exception.DuplicateEntityException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasUpdateRequest;
import de.greluc.krt.iri.basetool.backend.repository.MaterialExternalAliasRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business operations on {@link MaterialExternalAlias}. The service is the single seam between the
 * admin REST controller and the JPA repository — controllers must not inject the repository
 * directly (enforced by the {@code controllerLayerShouldNotDependOnRepositoryLayer} ArchUnit rule).
 *
 * <p>{@code createdBy} is stamped from the JWT principal name on every create. The V108 seed
 * inserts use the literal {@code "system"} so admin-created rows are distinguishable from the
 * R1-seeded fuzzy / manual aliases in audit views.
 *
 * <p>The {@code (sourceSystem, externalName)} uniqueness is enforced both by the DB constraint
 * (catch-all defence) and pre-emptively here so the caller gets a clean {@link
 * DuplicateEntityException} → 409 instead of a generic {@code DataIntegrityViolationException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialExternalAliasService {

  private final MaterialExternalAliasRepository repository;
  private final MaterialRepository materialRepository;
  private final AuthHelperService authHelperService;

  /**
   * Returns every alias sorted by external name. Drives the admin table view.
   *
   * @return all alias rows, sorted alphabetically by external_name
   */
  public List<MaterialExternalAlias> findAll() {
    return repository.findAllByOrderByExternalNameAsc();
  }

  /**
   * Looks up an alias by id.
   *
   * @param id alias UUID
   * @return the alias entity
   * @throws NotFoundException if no row exists for the given id
   */
  public MaterialExternalAlias findById(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new NotFoundException("Material external alias " + id + " does not exist."));
  }

  /**
   * Resolution-chain lookup consumed by the R3 SC Wiki commodity sync (and the R6 UEX counterpart).
   * Case-insensitive on {@code externalName} so a patch-version casing drift on the upstream side
   * still resolves to the curated row.
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName the external commodity name (case-insensitive match)
   * @return the resolved material if an alias exists, {@code null} otherwise
   */
  public Material resolveMaterialByAlias(
      MaterialExternalAliasSource sourceSystem, String externalName) {
    if (externalName == null || externalName.isBlank()) {
      return null;
    }
    return repository
        .findBySourceSystemAndExternalNameIgnoreCase(sourceSystem, externalName)
        .map(MaterialExternalAlias::getMaterial)
        .orElse(null);
  }

  /**
   * Persists a new alias. Validates that the referenced material exists and that no alias with the
   * same {@code (sourceSystem, externalName)} exists yet; on a duplicate the row is NOT saved and a
   * {@link DuplicateEntityException} is thrown so the controller can map it to HTTP 409.
   *
   * <p>{@code createdBy} is stamped from the authenticated principal — {@code "system"} when no
   * principal can be resolved (defensive default; the controller's {@code @PreAuthorize} gate
   * already requires {@code ROLE_ADMIN}, but tests run without a full security context).
   *
   * @param request validated create payload
   * @return the persisted alias row
   * @throws NotFoundException if {@code request.materialId()} does not point at a known material
   * @throws DuplicateEntityException if an alias for the same source / external name already exists
   */
  @Transactional
  public MaterialExternalAlias create(MaterialExternalAliasCreateRequest request) {
    MaterialExternalAliasSource source =
        MaterialExternalAliasSource.valueOf(request.sourceSystem());
    Material material =
        materialRepository
            .findById(request.materialId())
            .orElseThrow(
                () ->
                    new NotFoundException("Material " + request.materialId() + " does not exist."));
    repository
        .findBySourceSystemAndExternalName(source, request.externalName())
        .ifPresent(
            existing -> {
              throw new DuplicateEntityException(
                  "Alias '"
                      + request.externalName()
                      + "' already exists for source "
                      + source
                      + ".");
            });

    MaterialExternalAlias alias = new MaterialExternalAlias();
    alias.setMaterial(material);
    alias.setSourceSystem(source);
    alias.setExternalName(request.externalName());
    alias.setExternalKey(request.externalKey());
    alias.setExternalUuid(request.externalUuid());
    alias.setExternalCode(request.externalCode());
    alias.setNote(request.note());
    alias.setCreatedBy(currentPrincipalNameOrSystem());
    MaterialExternalAlias saved = repository.save(alias);
    log.info(
        "Admin alias created: source={} externalName='{}' material={} by={}",
        source,
        saved.getExternalName(),
        material.getName(),
        saved.getCreatedBy());
    return saved;
  }

  /**
   * Applies an update to an existing alias. The {@code version} on the request must match the row's
   * current {@code @Version} or Hibernate raises an optimistic-lock failure → HTTP 409 via {@link
   * de.greluc.krt.iri.basetool.backend.exception.GlobalExceptionHandler}.
   *
   * @param id alias UUID to update
   * @param request validated update payload
   * @return the persisted alias row
   * @throws NotFoundException if {@code id} or {@code request.materialId()} does not exist
   * @throws DuplicateEntityException if the new {@code (sourceSystem, externalName)} collides with
   *     a different row
   */
  @Transactional
  public MaterialExternalAlias update(UUID id, MaterialExternalAliasUpdateRequest request) {
    MaterialExternalAlias alias = findById(id);
    if (!Objects.equals(alias.getVersion(), request.version())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          MaterialExternalAlias.class, id);
    }
    MaterialExternalAliasSource source =
        MaterialExternalAliasSource.valueOf(request.sourceSystem());
    Material material =
        materialRepository
            .findById(request.materialId())
            .orElseThrow(
                () ->
                    new NotFoundException("Material " + request.materialId() + " does not exist."));
    repository
        .findBySourceSystemAndExternalName(source, request.externalName())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(
            other -> {
              throw new DuplicateEntityException(
                  "Alias '"
                      + request.externalName()
                      + "' already exists for source "
                      + source
                      + ".");
            });

    alias.setMaterial(material);
    alias.setSourceSystem(source);
    alias.setExternalName(request.externalName());
    alias.setExternalKey(request.externalKey());
    alias.setExternalUuid(request.externalUuid());
    alias.setExternalCode(request.externalCode());
    alias.setNote(request.note());
    MaterialExternalAlias saved = repository.save(alias);
    log.info("Admin alias updated: id={} by={}", saved.getId(), currentPrincipalNameOrSystem());
    return saved;
  }

  /**
   * Removes an alias by id. No referential side-effects: no other table references {@code
   * material_external_alias}.
   *
   * @param id alias UUID to delete
   * @throws NotFoundException if no row exists for the given id
   */
  @Transactional
  public void delete(UUID id) {
    MaterialExternalAlias alias = findById(id);
    repository.delete(alias);
    log.info("Admin alias deleted: id={} by={}", id, currentPrincipalNameOrSystem());
  }

  /**
   * Resolves the JWT principal name for the {@code createdBy} stamp, defaulting to {@code "system"}
   * when no principal is available. Centralised here so create / update / delete log lines stay
   * consistent.
   *
   * @return JWT subject of the caller, or {@code "system"} if the security context is empty
   */
  private String currentPrincipalNameOrSystem() {
    return authHelperService.currentAuthentication().map(Authentication::getName).orElse("system");
  }
}
