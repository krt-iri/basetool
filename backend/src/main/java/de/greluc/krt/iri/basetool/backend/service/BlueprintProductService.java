package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintProductDto;
import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintProductRow;
import de.greluc.krt.iri.basetool.backend.repository.BlueprintRepository;
import de.greluc.krt.iri.basetool.backend.repository.PersonalBlueprintRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service backing the user-facing blueprint product search (#327). Exposes the SC Wiki
 * blueprint master as a de-duplicated list of <em>products</em> (the unit of ownership): all active
 * recipes whose output name normalizes to the same {@code product_key} collapse into one entry,
 * carrying a variant count, an example Wiki key, the manufacturer (when resolved) and an "already
 * owned by the caller" flag.
 *
 * <p>Grouping happens in memory rather than in SQL because the {@code product_key} is a normalized
 * form of {@code output_name} (see {@link BlueprintNameNormalizer}) that PostgreSQL cannot compute.
 * The active blueprint set is on the order of 1600 rows, so loading the (optionally name-filtered)
 * rows and grouping them in Java is cheap and mirrors the existing UEX location search.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlueprintProductService {

  /** Default number of products returned when the caller does not specify a limit. */
  public static final int DEFAULT_LIMIT = 25;

  /** Hard cap on the number of products returned, regardless of the requested limit. */
  public static final int MAX_LIMIT = 200;

  private final BlueprintRepository blueprintRepository;
  private final PersonalBlueprintRepository personalBlueprintRepository;
  private final BlueprintNameNormalizer normalizer;

  /**
   * Searches the blueprint products by a case-insensitive substring of the product name, returning
   * up to {@code limit} (capped at {@link #MAX_LIMIT}) alphabetically sorted products, each flagged
   * with whether {@code ownerSub} already owns it.
   *
   * @param query case-insensitive product-name substring; {@code null} / blank returns all products
   * @param limit requested maximum number of products; clamped to {@code [1, MAX_LIMIT]}
   * @param ownerSub Keycloak {@code sub} of the caller, used to compute the owned flag
   * @return the matching products, alphabetically by name, capped to the effective limit
   */
  @NotNull
  public List<BlueprintProductDto> searchProducts(
      @Nullable String query, int limit, @NotNull String ownerSub) {
    int cap = Math.max(1, Math.min(limit, MAX_LIMIT));
    String q = query == null ? "" : query.trim();

    List<ProductAccumulator> products = new ArrayList<>(buildProductMap(q).values());
    products.sort(
        Comparator.comparing(
            p -> p.displayName, Comparator.nullsLast(String::compareToIgnoreCase)));
    List<ProductAccumulator> capped = products.size() > cap ? products.subList(0, cap) : products;

    Set<String> owned = ownedKeys(ownerSub, capped.stream().map(p -> p.productKey).toList());
    List<BlueprintProductDto> out = new ArrayList<>(capped.size());
    for (ProductAccumulator p : capped) {
      out.add(
          new BlueprintProductDto(
              p.productKey,
              p.displayName,
              p.variantCount,
              p.manufacturerName,
              p.exampleKey,
              owned.contains(p.productKey)));
    }
    return out;
  }

  /**
   * Resolves a normalized product key back to its canonical product (display name + optional
   * resolved output-item id). Used by the add flow (Phase 3) and the import (Phase 4) to stamp a
   * new ownership row. Returns empty for a blank key or one that no active blueprint produces.
   *
   * @param productKey normalized product key
   * @return the resolved product, or empty if unknown
   */
  @NotNull
  public Optional<ResolvedProduct> resolveByProductKey(@Nullable String productKey) {
    if (productKey == null || productKey.isBlank()) {
      return Optional.empty();
    }
    ProductAccumulator p = buildProductMap("").get(productKey);
    return p == null
        ? Optional.empty()
        : Optional.of(new ResolvedProduct(p.productKey, p.displayName, p.outputItemId));
  }

  /**
   * Loads the active blueprint rows matching {@code q} and groups them by normalized product key,
   * preserving first-seen order. Each group records the first display name, the recipe count, and
   * the first non-null example key / manufacturer / output-item id.
   *
   * @param q case-insensitive output-name substring ({@code ""} = no filter)
   * @return product accumulators keyed by normalized product key
   */
  private Map<String, ProductAccumulator> buildProductMap(String q) {
    Map<String, ProductAccumulator> map = new LinkedHashMap<>();
    for (BlueprintProductRow row : blueprintRepository.findActiveProductRows(q)) {
      if (row.outputName() == null) {
        continue;
      }
      String key = normalizer.normalize(row.outputName());
      if (key.isEmpty()) {
        continue;
      }
      ProductAccumulator acc =
          map.computeIfAbsent(key, k -> new ProductAccumulator(k, row.outputName()));
      acc.variantCount++;
      if (acc.exampleKey == null && row.scwikiKey() != null) {
        acc.exampleKey = row.scwikiKey();
      }
      if (acc.manufacturerName == null && row.manufacturerName() != null) {
        acc.manufacturerName = row.manufacturerName();
      }
      if (acc.outputItemId == null && row.outputItemId() != null) {
        acc.outputItemId = row.outputItemId();
      }
    }
    return map;
  }

  /**
   * Returns the subset of {@code keys} the owner already owns, via a single bulk lookup.
   *
   * @param ownerSub Keycloak {@code sub} of the owner
   * @param keys the product keys to test
   * @return the owned product keys
   */
  private Set<String> ownedKeys(String ownerSub, List<String> keys) {
    if (keys.isEmpty()) {
      return Set.of();
    }
    Set<String> out = new HashSet<>();
    for (PersonalBlueprint pb :
        personalBlueprintRepository.findAllByOwnerSubAndProductKeyIn(ownerSub, keys)) {
      out.add(pb.getProductKey());
    }
    return out;
  }

  /** Mutable per-product grouping accumulator used while collapsing recipe rows into products. */
  private static final class ProductAccumulator {
    private final String productKey;
    private final String displayName;
    private int variantCount;
    private String exampleKey;
    private String manufacturerName;
    private UUID outputItemId;

    private ProductAccumulator(String productKey, String displayName) {
      this.productKey = productKey;
      this.displayName = displayName;
    }
  }

  /**
   * A product key resolved back to its canonical product, for stamping a new ownership row.
   *
   * @param productKey normalized product key
   * @param productName canonical display name
   * @param outputItemId resolved output {@code game_item} id, or {@code null} if unresolved
   */
  public record ResolvedProduct(String productKey, String productName, UUID outputItemId) {}
}
