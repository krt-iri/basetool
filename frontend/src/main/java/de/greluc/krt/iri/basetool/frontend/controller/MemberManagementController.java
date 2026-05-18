package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserAttributesUpdateDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.MemberEditForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller for the squadron member-management pages ({@code /members}).
 *
 * <p>Lists, searches and edits squadron members. The {@code logistician}/{@code mission-manager}
 * toggles call the backend's PATCH endpoints directly and return the updated record as JSON for
 * inline UI updates — those flags are independent of Keycloak realm roles and can be granted by an
 * admin without round-tripping through Keycloak (the JWT converter then re-promotes them to {@code
 * ROLE_LOGISTICIAN}/{@code ROLE_MISSION_MANAGER} on the next login).
 */
@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class MemberManagementController {

  private final BackendApiClient backendApiClient;

  /**
   * Renders the member list, optionally filtered by free-text search and paginated.
   *
   * @param search optional search query; switches the underlying endpoint from {@code /users} to
   *     {@code /users/search}
   * @param page zero-based page index
   * @param size page size
   * @param model Thymeleaf model populated with users, page metadata and the echoed search query
   * @return the {@code members} view name
   */
  @GetMapping
  public String listMembers(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      Model model) {
    try {
      String base =
          (search == null || search.isBlank())
              ? "/api/v1/users"
              : "/api/v1/users/search?query=" + search;
      StringBuilder uri = new StringBuilder(base);
      uri.append(base.contains("?") ? "&" : "?");
      if (page != null) {
        uri.append("page=").append(page).append("&");
      }
      if (size != null) {
        uri.append("size=").append(size).append("&");
      }
      uri.append("sort=username,asc");

      PageResponse<UserDto> pageResponse =
          backendApiClient.get(
              uri.toString(), new ParameterizedTypeReference<PageResponse<UserDto>>() {});
      List<UserDto> users = pageResponse == null ? null : pageResponse.content();
      model.addAttribute("users", users);
      model.addAttribute("usersPage", pageResponse);
      model.addAttribute("search", search);
    } catch (Exception e) {
      log.error("Could not fetch members", e);
      model.addAttribute("error", "error.members.load");
    }
    return "members";
  }

  /**
   * AJAX search endpoint backing the member-picker typeahead. Returns the unwrapped content list
   * (single hard-coded page of size 1000 — autocompletes are short, one page is enough).
   *
   * @param query free-text query forwarded to the backend
   * @return matching users or {@code null} when the backend returns no page
   */
  @GetMapping("/api/search")
  @ResponseBody
  public List<UserDto> searchMembers(@RequestParam String query) {
    PageResponse<UserDto> page =
        backendApiClient.get(
            "/api/v1/users/search?query=" + query + "&size=1000&sort=username,asc",
            new ParameterizedTypeReference<PageResponse<UserDto>>() {});
    return page == null ? null : page.content();
  }

  /**
   * Renders the member edit page.
   *
   * <p>{@code source} threads through the form so a "Save" landing here from the profile page can
   * redirect back to {@code /profile} on success while a save reached through the member list stays
   * on the member list. If the model already carries a {@code MemberEditForm} (because a
   * validation-failure rerender happened) the source is patched in but the other form fields are
   * preserved.
   *
   * @param id user id
   * @param source optional origin marker ({@code "profile"} keeps the round-trip on profile)
   * @param model Thymeleaf model populated with {@code user} and {@code memberEditForm}
   * @param redirectAttributes flash attributes carrier for the error redirect
   * @return inline {@code member-edit} view, or redirect to {@code /members} on backend failure
   */
  @GetMapping("/{id}/edit")
  public String editMember(
      @PathVariable @NotNull UUID id,
      @RequestParam(required = false) String source,
      Model model,
      RedirectAttributes redirectAttributes) {
    try {
      UserDto user = backendApiClient.get("/api/v1/users/" + id, UserDto.class);
      model.addAttribute("user", user);
      if (!model.containsAttribute("memberEditForm")) {
        model.addAttribute(
            "memberEditForm",
            new MemberEditForm(
                user.rank(),
                user.description(),
                user.displayName(),
                user.version(),
                source,
                user.joinDate()));
      } else {
        MemberEditForm form = (MemberEditForm) model.getAttribute("memberEditForm");
        if (form != null && form.source() == null) {
          model.addAttribute(
              "memberEditForm",
              new MemberEditForm(
                  form.rank(),
                  form.description(),
                  form.displayName(),
                  form.version(),
                  source,
                  form.joinDate()));
        }
      }
      return "member-edit";
    } catch (Exception e) {
      log.error("Could not fetch member details", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.member.details.load");
      return "redirect:/members";
    }
  }

  /**
   * Persists member edits. Validation failure re-renders inline via {@link #editMember}. Successful
   * save with {@code source=profile} redirects back to the profile page; everything else lands on
   * the member list.
   *
   * @param id user id
   * @param form member edit form
   * @param bindingResult validation errors carrier
   * @param model Thymeleaf model used for inline re-rendering
   * @param redirectAttributes flash attributes carrier
   * @return redirect target depending on source and outcome
   */
  @PostMapping("/{id}/edit")
  public String updateMember(
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("memberEditForm") MemberEditForm form,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      // Re-render the edit view directly; the BindingResult stays request-scoped so
      // it never goes through a Redis-serialised FlashMap (see RedisSessionConfig).
      return editMember(id, form.source(), model, redirectAttributes);
    }
    try {
      UserAttributesUpdateDto body =
          new UserAttributesUpdateDto(
              form.rank(), form.description(), form.displayName(), form.version(), form.joinDate());
      backendApiClient.put("/api/v1/users/" + id + "/attributes", body, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "notification.success.save");
      if ("profile".equals(form.source())) {
        return "redirect:/profile";
      }
    } catch (Exception e) {
      log.error("Update failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.member.update.failed");
      return "redirect:/members/"
          + id
          + "/edit"
          + (form.source() != null ? "?source=" + form.source() : "");
    }
    return "redirect:/members";
  }

  /**
   * AJAX endpoint: flips the {@code is_logistician} flag on a user. The backend handles the
   * underlying authority promotion so the next JWT minted by Keycloak picks up {@code
   * ROLE_LOGISTICIAN}.
   *
   * @param id user id
   * @param isLogistician desired new flag value
   * @return the updated user record
   */
  @PostMapping("/{id}/logistician")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseBody
  public UserDto toggleLogistician(@PathVariable UUID id, @RequestParam boolean isLogistician) {
    return backendApiClient.patch(
        "/api/v1/users/" + id + "/logistician?isLogistician=" + isLogistician, null, UserDto.class);
  }

  /**
   * AJAX endpoint: flips the {@code is_mission_manager} flag on a user.
   *
   * @param id user id
   * @param isMissionManager desired new flag value
   * @return the updated user record
   */
  @PostMapping("/{id}/mission-manager")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseBody
  public UserDto toggleMissionManager(
      @PathVariable UUID id, @RequestParam boolean isMissionManager) {
    return backendApiClient.patch(
        "/api/v1/users/" + id + "/mission-manager?isMissionManager=" + isMissionManager,
        null,
        UserDto.class);
  }

  /**
   * Deletes a user. Admin-only — the OFFICER role at the class level is intentionally narrowed
   * here.
   *
   * @param id user id
   * @param redirectAttributes flash attributes carrier
   * @return redirect to {@code /members}
   */
  @PostMapping("/{id}/delete")
  @PreAuthorize("hasRole('ADMIN')")
  public String deleteMember(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/users/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "success.user.delete");
    } catch (Exception e) {
      log.error("Delete failed", e);
      redirectAttributes.addFlashAttribute("errorToast", "error.user.delete");
    }
    return "redirect:/members";
  }
}
