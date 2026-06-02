package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.form.ProfileDescriptionForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the user profile page ({@code /profile}).
 *
 * <p>Renders the current user's profile data and exposes a single editable field (description +
 * display name). The page layers two data sources: the OIDC token claims (used as the immediate
 * default) and the backend {@code /api/v1/users/me} payload (used to overwrite the token values
 * with the authoritative DB state, including the optimistic-locking {@code version} needed for
 * subsequent updates). When the backend is unreachable, the token-only view still renders so the
 * user always sees something.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

  private final BackendApiClient backendApiClient;

  @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
  private String issuerUri;

  /**
   * Renders the profile page. Unauthenticated users are redirected to the home page. For
   * authenticated users the controller seeds the model from token claims, then overlays the backend
   * {@code /me} record where available and parses the {@code joinDate} into a {@code LocalDate}
   * plus the derived months-in-squadron counter. A fresh {@link ProfileDescriptionForm} is added to
   * the model unless one is already there (preserves user input across a failed submit).
   *
   * @param model Thymeleaf model populated with the layered profile data and the description form
   * @param principal authenticated OIDC user
   * @return the {@code profile} view name, or {@code redirect:/} for guests
   */
  @GetMapping("/profile")
  public String profile(Model model, @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      return "redirect:/";
    }

    model.addAttribute("username", principal.getPreferredUsername());
    model.addAttribute("email", principal.getEmail());

    // Default from Token
    model.addAttribute("rank", getSingleClaim(principal, "rank"));
    model.addAttribute("description", getSingleClaim(principal, "description"));
    model.addAttribute("displayName", getSingleClaim(principal, "displayName"));

    // Fetch from Backend to get latest DB state
    try {
      Map<String, Object> user =
          backendApiClient.get(
              "/api/v1/users/me", new ParameterizedTypeReference<Map<String, Object>>() {});

      if (user != null) {
        if (user.get("rank") != null) {
          model.addAttribute("rank", user.get("rank"));
        }
        // Always overwrite description from DB, even if null (to allow clearing)
        // But if DB is null and Token has it? Prefer DB (it might have been deleted locally).
        // Actually, if DB has null, we might want to show empty.
        if (user.containsKey("description")) {
          model.addAttribute("description", user.get("description"));
        }
        if (user.containsKey("displayName")) {
          model.addAttribute("displayName", user.get("displayName"));
        }
        if (user.containsKey("version")) {
          model.addAttribute("version", parseLong(user.get("version")));
        }
        if (user.containsKey("joinDate") && user.get("joinDate") != null) {
          try {
            LocalDate joinDate = LocalDate.parse(String.valueOf(user.get("joinDate")));
            model.addAttribute("joinDate", joinDate);
            model.addAttribute(
                "monthsInSquadron", ChronoUnit.MONTHS.between(joinDate, LocalDate.now()));
          } catch (Exception ignored) {
            // joinDate missing or malformed — leave attribute unset
          }
        }
      }
    } catch (Exception e) {
      // Backend unavailable? Keep Token data.
    }

    model.addAttribute("keycloakAccountUrl", issuerUri + "/account");

    if (!model.containsAttribute("profileDescriptionForm")) {
      String currentDesc = (String) model.getAttribute("description");
      String currentDisplayName = (String) model.getAttribute("displayName");
      Long version = (Long) model.getAttribute("version");
      model.addAttribute(
          "profileDescriptionForm",
          new ProfileDescriptionForm(
              currentDesc, currentDisplayName, version != null ? version : 0L));
    }

    return "profile";
  }

  /**
   * Handles the description + display-name update form post.
   *
   * <p>Validation errors render the profile view inline (no redirect) so the {@link BindingResult}
   * stays request-scoped and never serializes through a Redis FlashMap (see {@code
   * RedisSessionConfig}). A 409 with problem type {@code concurrency-conflict} surfaces as a
   * dedicated optimistic-lock toast so the user knows to refresh and retry; any other failure lands
   * as a generic update-failed toast. {@code null} form fields are sent as the empty string — the
   * backend's {@link de.greluc.krt.iri.basetool.backend.config.NormalizedStringDeserializer} maps
   * that back to a blank, which is the intended "clear this field" semantics.
   *
   * @param form validated form payload
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used when re-rendering inline
   * @param principal authenticated OIDC user
   * @param redirectAttributes flash attributes carrier for the result toast
   * @return inline {@code profile} view on validation failure, otherwise redirect to {@code
   *     /profile}
   */
  @PostMapping("/profile/description")
  public String updateDescription(
      @Valid @ModelAttribute("profileDescriptionForm") ProfileDescriptionForm form,
      BindingResult bindingResult,
      Model model,
      @AuthenticationPrincipal OidcUser principal,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      // Render the profile view directly; the BindingResult stays request-scoped so it
      // never goes through a Redis-serialised FlashMap (see RedisSessionConfig).
      return profile(model, principal);
    }
    try {
      backendApiClient.put(
          "/api/v1/users/me/description",
          Map.of(
              "description", form.description() == null ? "" : form.description(),
              "displayName", form.displayName() == null ? "" : form.displayName(),
              "version", form.version()),
          Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
    } catch (de.greluc.krt.iri.basetool.frontend.service.BackendServiceException e) {
      log.error("Update failed", e);
      if (e.getStatusCode() == 409 && "concurrency-conflict".equals(e.getProblemType())) {
        redirectAttributes.addFlashAttribute("errorToast", "error.concurrency.conflict");
      } else {
        redirectAttributes.addFlashAttribute("errorToast", "error.profile.update.failed");
      }
      return "redirect:/profile";
    } catch (Exception e) {
      log.error("Update failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.profile.update.failed");
      return "redirect:/profile";
    }
    return "redirect:/profile";
  }

  private static Long parseLong(Object o) {
    if (o == null) {
      return 0L;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(o));
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private Object getSingleClaim(OidcUser principal, String claim) {
    Object value = principal.getAttribute(claim);
    if (value instanceof java.util.List<?> list && !list.isEmpty()) {
      return list.get(0);
    }
    return value;
  }
}
