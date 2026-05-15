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

  /** Schritt 5: Übersicht Beförderungssystem – öffentlich für alle eingeloggten Nutzer. */
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
    return "promotion-overview";
  }

  /**
   * Schritt 6: Meine Bewertungen – nur für den eingeloggten Nutzer selbst. Lädt zusätzlich die
   * Beförderbarkeits-Auswertung pro konfigurierter Rangstufen-Kombination und reicht sie an das
   * Template weiter, damit der Nutzer sieht, welche Rangsprünge bereits erreichbar sind und welche
   * Anforderungen noch fehlen.
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

    List<PromotionEligibilityDto> eligibilities = fetchMyEligibility();

    model.addAttribute("topics", topics);
    model.addAttribute("topicCategoryMap", topicCategoryMap);
    model.addAttribute("evaluationByCategoryId", evaluationByCategoryId);
    model.addAttribute("eligibilities", eligibilities);
    return "promotion-my-evaluations";
  }

  /**
   * Schritt 7: Bewertungsverwaltung – nur für ADMIN und OFFICER. Reicht zusätzlich die pro Mitglied
   * vorausberechnete Beförderbarkeitsliste an das Template; die Offiziere sehen dadurch unmittelbar
   * in der Übersicht, welcher Spieler für welche Beförderung bereit ist.
   */
  @GetMapping("/manage")
  @PreAuthorize("hasAnyRole('ADMIN','OFFICER')")
  public String manage(Model model) {
    List<PromotionTopicDto> topics = fetchTopics();
    // Flat list of all categories in sort order
    List<PromotionCategoryDto> allCategories = new ArrayList<>();
    for (PromotionTopicDto topic : topics) {
      allCategories.addAll(fetchCategoriesByTopic(topic.id().toString()));
    }

    // Fetch all evaluations for admin view
    List<MemberEvaluationDto> allEvaluations = fetchAllEvaluations();
    // Build map: userId+categoryId -> evaluation
    Map<String, MemberEvaluationDto> evaluationMap = new LinkedHashMap<>();
    for (MemberEvaluationDto eval : allEvaluations) {
      evaluationMap.put(eval.userId() + "_" + eval.categoryId(), eval);
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

    model.addAttribute("categories", allCategories);
    model.addAttribute("evaluationMap", evaluationMap);
    model.addAttribute("members", members);
    model.addAttribute("eligibilityByUser", eligibilityByUser);
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

    model.addAttribute("requirements", requirements);
    model.addAttribute("groupedRequirements", groupedRequirements);
    model.addAttribute("topics", fetchTopics());
    model.addAttribute("categories", fetchAllCategories());
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
