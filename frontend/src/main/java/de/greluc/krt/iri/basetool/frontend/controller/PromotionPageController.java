package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.MemberEvaluationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PromotionCategoryDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PromotionEligibilityDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PromotionLevelContentDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PromotionTopicDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.RankRequirementDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Frontend controller for the promotion system pages. */
@Controller
@RequestMapping("/promotion")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class PromotionPageController {

  private final BackendApiClient backendApiClient;

  /**
   * Schritt 5: Übersicht Beförderungssystem – öffentlich für alle eingeloggten Nutzer.
   *
   * <p>Reicht zusätzlich den aktuellen Rang des eingeloggten Nutzers (falls vorhanden) an das
   * Template weiter, damit dort ein "du-bist-hier"-Marker auf demjenigen Rangsprung gerendert
   * werden kann, der die nächste Beförderung des Nutzers betrifft ({@code fromRank ==
   * currentUserRank}). Ist der Rang nicht ermittelbar (z. B. Backend down, Guest-User ohne Rang im
   * DTO), wird {@code null} weitergegeben und der Marker einfach nicht angezeigt — die Seite
   * funktioniert weiterhin als reine Übersicht.
   */
  @GetMapping("/overview")
  public String overview(Model model) {
    List<PromotionTopicDto> topics = fetchTopics();
    Map<String, List<PromotionCategoryDto>> topicCategoryMap = new LinkedHashMap<>();
    Map<String, List<PromotionLevelContentDto>> categoryContentMap = new LinkedHashMap<>();

    for (PromotionTopicDto topic : topics) {
      List<PromotionCategoryDto> categories = fetchCategoriesByTopic(topic.id().toString());
      topicCategoryMap.put(topic.id().toString(), categories);
      for (PromotionCategoryDto category : categories) {
        List<PromotionLevelContentDto> contents = fetchLevelContents(category.id().toString());
        categoryContentMap.put(category.id().toString(), contents);
      }
    }

    List<RankRequirementDto> rankRequirements = fetchAllRankRequirements();
    Map<String, List<RankRequirementDto>> groupedRankRequirements = new LinkedHashMap<>();
    rankRequirements.stream()
        .sorted(
            java.util.Comparator.comparingInt(RankRequirementDto::fromRank)
                .thenComparingInt(RankRequirementDto::toRank))
        .forEach(
            req -> {
              String key = req.fromRank() + "_" + req.toRank();
              groupedRankRequirements.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
            });

    model.addAttribute("topics", topics);
    model.addAttribute("topicCategoryMap", topicCategoryMap);
    model.addAttribute("categoryContentMap", categoryContentMap);
    model.addAttribute("rankRequirements", rankRequirements);
    model.addAttribute("groupedRankRequirements", groupedRankRequirements);
    model.addAttribute("currentUserRank", fetchCurrentUserRank());
    return "promotion-overview";
  }

  /**
   * Schritt 6: Meine Bewertungen – nur für den eingeloggten Nutzer selbst. Lädt zusätzlich die
   * Beförderbarkeits-Auswertung pro konfigurierter Rangstufen-Kombination und reicht sie an das
   * Template weiter, damit der Nutzer sieht, welche Rangsprünge bereits erreichbar sind und welche
   * Anforderungen noch fehlen.
   *
   * <p>Zusätzlich wird {@code requiredLevelByCategory} berechnet: pro Kategorie die höchste
   * Mindeststufe, die irgendeine Rangvoraussetzung verlangt. Das Template kann damit Kategorien
   * markieren, in denen die eigene Bewertung unter dem höchsten Anforderungslevel liegt
   * ("Schwachstellen"-Highlighting), ohne dass der Nutzer alle Beförderbarkeits-Karten
   * gegenüberstellen muss.
   */
  @GetMapping("/my-evaluations")
  public String myEvaluations(Model model) {
    List<PromotionTopicDto> topics = fetchTopics();
    List<MemberEvaluationDto> myEvaluations = fetchMyEvaluations();

    // Build a map: categoryId -> evaluation for quick lookup
    Map<String, MemberEvaluationDto> evaluationByCategoryId = new LinkedHashMap<>();
    for (MemberEvaluationDto eval : myEvaluations) {
      evaluationByCategoryId.put(eval.categoryId().toString(), eval);
    }

    Map<String, List<PromotionCategoryDto>> topicCategoryMap = new LinkedHashMap<>();
    for (PromotionTopicDto topic : topics) {
      List<PromotionCategoryDto> categories = fetchCategoriesByTopic(topic.id().toString());
      topicCategoryMap.put(topic.id().toString(), categories);
    }

    // Per category the strictest minimum level any rank requirement demands. Used by the
    // template to highlight categories where the user's assigned level falls below the
    // highest expectation across all promotion steps. PromotionLevel ordering is A < B < C.
    Map<String, String> requiredLevelByCategory = new LinkedHashMap<>();
    for (RankRequirementDto req : fetchAllRankRequirements()) {
      if (req.categoryId() == null || req.minimumLevel() == null) {
        continue;
      }
      String key = req.categoryId().toString();
      String existing = requiredLevelByCategory.get(key);
      if (existing == null || compareLevels(req.minimumLevel(), existing) > 0) {
        requiredLevelByCategory.put(key, req.minimumLevel());
      }
    }

    List<PromotionEligibilityDto> eligibilities = fetchMyEligibility();

    model.addAttribute("topics", topics);
    model.addAttribute("topicCategoryMap", topicCategoryMap);
    model.addAttribute("evaluationByCategoryId", evaluationByCategoryId);
    model.addAttribute("eligibilities", eligibilities);
    model.addAttribute("requiredLevelByCategory", requiredLevelByCategory);
    model.addAttribute("currentUserRank", fetchCurrentUserRank());
    return "promotion-my-evaluations";
  }

  /**
   * Returns a positive number iff {@code a} is a strictly higher promotion level than {@code b}.
   * The ordering matches {@link de.greluc.krt.iri.basetool.backend.model.PromotionLevel} on the
   * backend (LEVEL_A &lt; LEVEL_B &lt; LEVEL_C). Unknown or null inputs sort below known values so
   * the highest-known wins when iterating.
   *
   * @param a first level identifier (e.g. {@code "LEVEL_B"}); may be {@code null}
   * @param b second level identifier; may be {@code null}
   * @return positive iff {@code a > b}, negative iff {@code a < b}, zero otherwise
   */
  private int compareLevels(String a, String b) {
    return levelOrdinal(a) - levelOrdinal(b);
  }

  private int levelOrdinal(String level) {
    if (level == null) {
      return -1;
    }
    return switch (level) {
      case "LEVEL_A" -> 0;
      case "LEVEL_B" -> 1;
      case "LEVEL_C" -> 2;
      default -> -1;
    };
  }

  /**
   * Schritt 7: Bewertungsverwaltung – nur für ADMIN und OFFICER. Reicht zusätzlich die pro Mitglied
   * vorausberechnete Beförderbarkeitsliste an das Template; die Offiziere sehen dadurch unmittelbar
   * in der Übersicht, welcher Spieler für welche Beförderung bereit ist.
   *
   * <p>Beyond the flat list of categories the view also receives a stable {@code categoriesByTopic}
   * map so the template can render a two-row header (topic group on top, categories beneath) and a
   * {@code categoryCountByTopic} map so each topic header cell knows the exact colspan to use. The
   * order of {@link PromotionTopicDto} entries in {@code topics} matches the column order of {@code
   * allCategories}, which is what the row body relies on.
   */
  @GetMapping("/manage")
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public String manage(Model model) {
    List<PromotionTopicDto> topics = fetchTopics();
    // Flat list of all categories in topic-then-sortOrder order. Built in lock-step with the
    // per-topic map so the template can iterate row cells against the flat list and header
    // cells against the grouped map without re-sorting on the view layer.
    List<PromotionCategoryDto> allCategories = new ArrayList<>();
    Map<String, List<PromotionCategoryDto>> categoriesByTopic = new LinkedHashMap<>();
    Map<String, Integer> categoryCountByTopic = new LinkedHashMap<>();
    for (PromotionTopicDto topic : topics) {
      List<PromotionCategoryDto> topicCategories = fetchCategoriesByTopic(topic.id().toString());
      categoriesByTopic.put(topic.id().toString(), topicCategories);
      categoryCountByTopic.put(topic.id().toString(), topicCategories.size());
      allCategories.addAll(topicCategories);
    }

    // Fetch all evaluations for admin view
    List<MemberEvaluationDto> allEvaluations = fetchAllEvaluations();
    // Build map: userId+categoryId -> evaluation
    Map<String, MemberEvaluationDto> evaluationMap = new LinkedHashMap<>();
    // Per-user latest updatedAt across all categories. Used by the template to render a
    // "letzte Aenderung am" tooltip on the member cell so officers can spot members whose
    // assessment has gone stale without having to scan every column.
    Map<String, java.time.Instant> lastEvaluatedByUser = new LinkedHashMap<>();
    // Per-user "has at least one stored evaluation" flag. Used by the client-side filter
    // "nur Mitglieder ohne Bewertung" so it does not have to inspect every cell in the row.
    Map<String, Boolean> hasEvaluationsByUser = new LinkedHashMap<>();
    for (MemberEvaluationDto eval : allEvaluations) {
      evaluationMap.put(eval.userId() + "_" + eval.categoryId(), eval);
      hasEvaluationsByUser.put(eval.userId(), Boolean.TRUE);
      if (eval.updatedAt() != null) {
        java.time.Instant prev = lastEvaluatedByUser.get(eval.userId());
        if (prev == null || eval.updatedAt().isAfter(prev)) {
          lastEvaluatedByUser.put(eval.userId(), eval.updatedAt());
        }
      }
    }

    // Fetch all members
    List<de.greluc.krt.iri.basetool.frontend.model.dto.UserDto> members = fetchMembers();

    // Eligibility per member, keyed by member.id (stringified UUID) so the template can
    // look it up cheaply. Failures for a single member don't break the whole page.
    Map<String, List<PromotionEligibilityDto>> eligibilityByUser = new LinkedHashMap<>();
    for (de.greluc.krt.iri.basetool.frontend.model.dto.UserDto member : members) {
      if (member.id() != null) {
        eligibilityByUser.put(
            member.id().toString(), fetchEligibilityForUser(member.id().toString()));
      }
    }

    model.addAttribute("topics", topics);
    model.addAttribute("categoriesByTopic", categoriesByTopic);
    model.addAttribute("categoryCountByTopic", categoryCountByTopic);
    model.addAttribute("categories", allCategories);
    model.addAttribute("evaluationMap", evaluationMap);
    model.addAttribute("members", members);
    model.addAttribute("eligibilityByUser", eligibilityByUser);
    model.addAttribute("lastEvaluatedByUser", lastEvaluatedByUser);
    model.addAttribute("hasEvaluationsByUser", hasEvaluationsByUser);
    return "promotion-manage";
  }

  /** Schritt 8: Admin-Bereich – Themenbereiche, Kategorien & Stufeninhalte verwalten. */
  @GetMapping("/admin/topics")
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public String adminTopics(Model model) {
    List<PromotionTopicDto> topics = fetchTopics();
    Map<String, List<PromotionCategoryDto>> topicCategoryMap = new LinkedHashMap<>();
    Map<String, List<PromotionLevelContentDto>> categoryContentMap = new LinkedHashMap<>();
    for (PromotionTopicDto topic : topics) {
      List<PromotionCategoryDto> categories = fetchCategoriesByTopic(topic.id().toString());
      topicCategoryMap.put(topic.id().toString(), categories);
      for (PromotionCategoryDto category : categories) {
        categoryContentMap.put(
            category.id().toString(), fetchLevelContents(category.id().toString()));
      }
    }
    model.addAttribute("topics", topics);
    model.addAttribute("topicCategoryMap", topicCategoryMap);
    model.addAttribute("categoryContentMap", categoryContentMap);
    return "promotion-admin-topics";
  }

  /**
   * Schritt 8: Admin-Bereich – Rangvoraussetzungen verwalten. The admin view groups the flat list
   * of requirements by their {@code (fromRank, toRank)} pair so each promotion step is rendered as
   * one section with its own table, mirroring the read-only layout used on {@code
   * /promotion/overview}.
   *
   * @param model Spring MVC model populated with the grouped requirements, topics and categories
   * @return the Thymeleaf view name for the rank-requirements admin page
   */
  @GetMapping("/admin/rank-requirements")
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public String adminRankRequirements(Model model) {
    List<RankRequirementDto> requirements = fetchAllRankRequirements();

    Map<String, List<RankRequirementDto>> groupedRequirements = new LinkedHashMap<>();
    requirements.stream()
        .sorted(
            java.util.Comparator.comparingInt(RankRequirementDto::fromRank)
                .thenComparingInt(RankRequirementDto::toRank))
        .forEach(
            req -> {
              String key = req.fromRank() + "_" + req.toRank();
              groupedRequirements.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
            });

    // categoriesByTopic powers the cascading Topic -> Category dropdown in the
    // create/edit modals: when the admin picks a topic, the client filters the
    // category dropdown to that topic's categories only, so a category from a
    // different topic can no longer be combined with a topic accidentally.
    List<PromotionTopicDto> topics = fetchTopics();
    Map<String, List<PromotionCategoryDto>> categoriesByTopic = new LinkedHashMap<>();
    for (PromotionTopicDto topic : topics) {
      categoriesByTopic.put(topic.id().toString(), fetchCategoriesByTopic(topic.id().toString()));
    }

    model.addAttribute("requirements", requirements);
    model.addAttribute("groupedRequirements", groupedRequirements);
    model.addAttribute("topics", topics);
    model.addAttribute("categories", fetchAllCategories());
    model.addAttribute("categoriesByTopic", categoriesByTopic);
    return "promotion-admin-rank-requirements";
  }

  // ---------------------------------------------------------------------------------
  // Private helper methods
  // ---------------------------------------------------------------------------------

  private List<PromotionTopicDto> fetchTopics() {
    try {
      List<PromotionTopicDto> result =
          backendApiClient.get(
              "/api/v1/promotion/topics/all", new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch promotion topics", e);
      return new ArrayList<>();
    }
  }

  private List<PromotionCategoryDto> fetchCategoriesByTopic(String topicId) {
    try {
      List<PromotionCategoryDto> result =
          backendApiClient.get(
              "/api/v1/promotion/categories/by-topic/" + topicId + "/all",
              new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch categories for topic {}", topicId, e);
      return new ArrayList<>();
    }
  }

  private List<PromotionCategoryDto> fetchAllCategories() {
    try {
      PageResponse<PromotionCategoryDto> result =
          backendApiClient.get(
              "/api/v1/promotion/categories?size=1000", new ParameterizedTypeReference<>() {});
      return result != null && result.content() != null ? result.content() : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch all categories", e);
      return new ArrayList<>();
    }
  }

  private List<PromotionLevelContentDto> fetchLevelContents(String categoryId) {
    try {
      List<PromotionLevelContentDto> result =
          backendApiClient.get(
              "/api/v1/promotion/level-contents/by-category/" + categoryId,
              new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch level contents for category {}", categoryId, e);
      return new ArrayList<>();
    }
  }

  private List<RankRequirementDto> fetchAllRankRequirements() {
    try {
      PageResponse<RankRequirementDto> result =
          backendApiClient.get(
              "/api/v1/promotion/rank-requirements?size=1000&sort=fromRank",
              new ParameterizedTypeReference<>() {});
      return result != null && result.content() != null ? result.content() : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch rank requirements", e);
      return new ArrayList<>();
    }
  }

  /**
   * Returns the rank of the currently authenticated user, or {@code null} if no rank is set or the
   * {@code /me} call fails. Used by the overview and my-evaluations pages to render a
   * "you-are-here" marker on the relevant promotion step. A {@code null} return is non-fatal — the
   * calling templates render without the marker rather than failing the whole page.
   *
   * @return the user's rank as an {@link Integer}, or {@code null} when unavailable
   */
  private Integer fetchCurrentUserRank() {
    try {
      de.greluc.krt.iri.basetool.frontend.model.dto.UserDto me =
          backendApiClient.get("/api/v1/users/me", new ParameterizedTypeReference<>() {});
      return me != null ? me.rank() : null;
    } catch (Exception e) {
      log.warn("Failed to fetch current user rank for promotion overview", e);
      return null;
    }
  }

  private List<MemberEvaluationDto> fetchMyEvaluations() {
    try {
      List<MemberEvaluationDto> result =
          backendApiClient.get(
              "/api/v1/promotion/evaluations/my", new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch my evaluations", e);
      return new ArrayList<>();
    }
  }

  private List<MemberEvaluationDto> fetchAllEvaluations() {
    try {
      PageResponse<MemberEvaluationDto> result =
          backendApiClient.get(
              "/api/v1/promotion/evaluations/all?size=10000",
              new ParameterizedTypeReference<>() {});
      return result != null && result.content() != null ? result.content() : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch all evaluations", e);
      return new ArrayList<>();
    }
  }

  private List<de.greluc.krt.iri.basetool.frontend.model.dto.UserDto> fetchMembers() {
    try {
      PageResponse<de.greluc.krt.iri.basetool.frontend.model.dto.UserDto> result =
          backendApiClient.get("/api/v1/users?size=1000", new ParameterizedTypeReference<>() {});
      return result != null && result.content() != null ? result.content() : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch members", e);
      return new ArrayList<>();
    }
  }

  private List<PromotionEligibilityDto> fetchMyEligibility() {
    try {
      List<PromotionEligibilityDto> result =
          backendApiClient.get(
              "/api/v1/promotion/eligibility/my", new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch personal promotion eligibility", e);
      return new ArrayList<>();
    }
  }

  private List<PromotionEligibilityDto> fetchEligibilityForUser(String userId) {
    try {
      List<PromotionEligibilityDto> result =
          backendApiClient.get(
              "/api/v1/promotion/eligibility/user/" + userId,
              new ParameterizedTypeReference<>() {});
      return result != null ? result : new ArrayList<>();
    } catch (Exception e) {
      log.error("Failed to fetch promotion eligibility for member {}", userId, e);
      return new ArrayList<>();
    }
  }
}
