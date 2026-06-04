/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.dto.BlueprintImportSuggestionDto;
import de.greluc.krt.iri.basetool.backend.service.BlueprintProductService.ResolvedProduct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Dependency-free fuzzy matcher used by the SCMDB import (#327, Phase 4) to suggest master products
 * for an external blueprint name that did not match exactly or via an alias. Scoring blends two
 * cheap, complementary signals computed over the already-normalized product keys (see {@link
 * BlueprintNameNormalizer}):
 *
 * <ul>
 *   <li><strong>Levenshtein ratio</strong> — {@code 1 - distance / maxLength} — catches character
 *       drift (typos, a missing letter, a stray plural).
 *   <li><strong>Token-set Jaccard</strong> — overlap of the space-split word sets — catches word
 *       reordering and extra / missing qualifier words that edit distance penalizes too harshly.
 * </ul>
 *
 * <p>The per-candidate score is the larger of the two signals, so either kind of similarity can
 * carry a suggestion. Implemented standalone rather than via {@code commons-text} to avoid pulling
 * a new runtime dependency for one screen.
 */
@Component
public class BlueprintFuzzyMatcher {

  /** Default minimum score for a candidate to be offered as a suggestion. */
  public static final double DEFAULT_THRESHOLD = 0.5;

  /** Default maximum number of suggestions returned per external name. */
  public static final int DEFAULT_LIMIT = 5;

  /**
   * Scores every candidate against the normalized query key and returns the best {@code limit}
   * candidates whose score reaches {@code threshold}, highest score first. Ties break
   * alphabetically by product name for a stable order.
   *
   * @param normalizedQuery normalized external name (already lowercased / whitespace-folded)
   * @param candidates the master product set to score against
   * @param limit maximum number of suggestions to return ({@code <= 0} yields an empty list)
   * @param threshold minimum score in {@code [0.0, 1.0]} for a candidate to qualify
   * @return the top matching products as suggestion DTOs, highest score first
   */
  @NotNull
  public List<BlueprintImportSuggestionDto> topSuggestions(
      @NotNull String normalizedQuery,
      @NotNull List<ResolvedProduct> candidates,
      int limit,
      double threshold) {
    if (limit <= 0 || normalizedQuery.isEmpty()) {
      return List.of();
    }
    Set<String> queryTokens = tokenize(normalizedQuery);
    List<BlueprintImportSuggestionDto> scored = new ArrayList<>();
    for (ResolvedProduct candidate : candidates) {
      double score = score(normalizedQuery, queryTokens, candidate.productKey());
      if (score >= threshold) {
        scored.add(
            new BlueprintImportSuggestionDto(
                candidate.productKey(), candidate.productName(), round(score)));
      }
    }
    scored.sort(
        Comparator.comparingDouble(BlueprintImportSuggestionDto::score)
            .reversed()
            .thenComparing(
                BlueprintImportSuggestionDto::productName,
                Comparator.nullsLast(String::compareToIgnoreCase)));
    return scored.size() > limit ? new ArrayList<>(scored.subList(0, limit)) : scored;
  }

  /**
   * Computes the blended similarity score between the query and one candidate key: the larger of
   * the Levenshtein ratio and the token-set Jaccard similarity.
   *
   * @param query normalized query key
   * @param queryTokens pre-split token set of the query (avoids re-splitting per candidate)
   * @param candidateKey normalized candidate product key
   * @return the similarity score in {@code [0.0, 1.0]}
   */
  private double score(
      @NotNull String query, @NotNull Set<String> queryTokens, @NotNull String candidateKey) {
    if (candidateKey.isEmpty()) {
      return 0.0;
    }
    double lev = levenshteinRatio(query, candidateKey);
    double jaccard = jaccard(queryTokens, tokenize(candidateKey));
    return Math.max(lev, jaccard);
  }

  /**
   * Levenshtein similarity ratio: {@code 1 - editDistance / max(len(a), len(b))}, clamped to {@code
   * [0.0, 1.0]}. Two empty strings score 1.0; an empty against a non-empty scores 0.0.
   *
   * @param a first string
   * @param b second string
   * @return the similarity ratio
   */
  private double levenshteinRatio(@NotNull String a, @NotNull String b) {
    int maxLen = Math.max(a.length(), b.length());
    if (maxLen == 0) {
      return 1.0;
    }
    return 1.0 - (double) levenshteinDistance(a, b) / maxLen;
  }

  /**
   * Classic two-row Levenshtein edit distance (insertions, deletions, substitutions all cost 1).
   * Uses {@code O(min(len) )} extra space by keeping only the previous and current rows.
   *
   * @param a first string
   * @param b second string
   * @return the minimum number of single-character edits to turn {@code a} into {@code b}
   */
  private int levenshteinDistance(@NotNull String a, @NotNull String b) {
    int n = a.length();
    int m = b.length();
    if (n == 0) {
      return m;
    }
    if (m == 0) {
      return n;
    }
    int[] previous = new int[m + 1];
    int[] current = new int[m + 1];
    for (int j = 0; j <= m; j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= n; i++) {
      current[0] = i;
      char ca = a.charAt(i - 1);
      for (int j = 1; j <= m; j++) {
        int cost = ca == b.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[m];
  }

  /**
   * Jaccard similarity of two token sets: {@code |intersection| / |union|}. Two empty sets score
   * 0.0 (no shared signal to lean on).
   *
   * @param a first token set
   * @param b second token set
   * @return the Jaccard similarity in {@code [0.0, 1.0]}
   */
  private double jaccard(@NotNull Set<String> a, @NotNull Set<String> b) {
    if (a.isEmpty() || b.isEmpty()) {
      return 0.0;
    }
    int intersection = 0;
    for (String token : a) {
      if (b.contains(token)) {
        intersection++;
      }
    }
    int union = a.size() + b.size() - intersection;
    return union == 0 ? 0.0 : (double) intersection / union;
  }

  /**
   * Splits a normalized key into its space-separated word tokens (empty tokens dropped).
   *
   * @param normalized a normalized product key
   * @return the token set (never {@code null})
   */
  @NotNull
  private Set<String> tokenize(@NotNull String normalized) {
    Set<String> tokens = new HashSet<>();
    for (String token : normalized.split(" ")) {
      if (!token.isEmpty()) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  /**
   * Rounds a score to three decimal places so the wire payload stays compact and stable.
   *
   * @param score the raw score
   * @return the score rounded to three decimals
   */
  private double round(double score) {
    return Math.round(score * 1000.0) / 1000.0;
  }
}
