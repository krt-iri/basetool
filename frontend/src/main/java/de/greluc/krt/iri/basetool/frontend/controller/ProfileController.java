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

@Controller
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

  private final BackendApiClient backendApiClient;

  @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
  private String issuerUri;

  @GetMapping("/profile")
  public String profile(Model model, @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      return "redirect:/";
    }

    model.addAttribute("username", principal.getPreferredUsername());
    model.addAttribute("firstName", principal.getGivenName());
    model.addAttribute("lastName", principal.getFamilyName());
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
        if (user.get("rank") != null) model.addAttribute("rank", user.get("rank"));
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
    if (o == null) return 0L;
    if (o instanceof Number n) return n.longValue();
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
