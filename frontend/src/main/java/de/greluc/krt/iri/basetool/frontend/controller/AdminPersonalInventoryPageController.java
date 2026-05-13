package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemCreateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PersonalInventoryItemUpdateRequest;
import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.model.form.PersonalInventoryForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.iri.basetool.frontend.service.BackendServiceException;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin counterpart of {@link PersonalInventoryPageController}: lets administrators pick a target
 * user from the squadron member list and manage that user's personal inventory. Authorization is
 * enforced by {@code @PreAuthorize("hasRole('ADMIN')")} at the class level; the GET handler
 * additionally exposes the explicit "ADMIN MODE" banner flag to the template so the visual
 * distinction is unambiguous.
 */
@Controller
@RequestMapping("/admin/personal-inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminPersonalInventoryPageController {

  private final BackendApiClient backendApiClient;

  @GetMapping
  public String view(
      @RequestParam(required = false) String userSub,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort,
      Model model) {
    if (!model.containsAttribute("personalInventoryForm")) {
      model.addAttribute("personalInventoryForm", new PersonalInventoryForm());
    }

    List<UserDto> users = fetchUsers();
    model.addAttribute("users", users);
    model.addAttribute("selectedUserSub", userSub);
    model.addAttribute("filterQuery", q == null ? "" : q);
    model.addAttribute("adminMode", Boolean.TRUE);

    if (userSub != null && !userSub.isBlank()) {
      PageResponse<PersonalInventoryItemDto> items = fetchItems(userSub, q, page, size, sort);
      model.addAttribute("items", items != null ? items.content() : Collections.emptyList());
      model.addAttribute("page", items);
      UserDto selected =
          users.stream()
              .filter(u -> u.id() != null && userSub.equals(u.id().toString()))
              .findFirst()
              .orElse(null);
      model.addAttribute("selectedUser", selected);
    } else {
      model.addAttribute("items", Collections.emptyList());
    }

    return "admin/personal-inventory";
  }

  @PostMapping("/{userSub}/add")
  public String add(
      @PathVariable @NotNull String userSub,
      @Valid @ModelAttribute("personalInventoryForm") PersonalInventoryForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute(
          "org.springframework.validation.BindingResult.personalInventoryForm", bindingResult);
      redirectAttributes.addFlashAttribute("personalInventoryForm", form);
      redirectAttributes.addFlashAttribute("showItemModal", true);
      redirectAttributes.addFlashAttribute(
          "modalAction", "/admin/personal-inventory/" + userSub + "/add");
      return redirectToList(userSub);
    }

    try {
      PersonalInventoryItemCreateRequest request =
          new PersonalInventoryItemCreateRequest(
              form.getName(),
              form.getNote(),
              form.getLocationUexId(),
              form.getLocationType(),
              form.getQuantity());
      backendApiClient.post(
          "/api/v1/admin/personal-inventory/" + userSub, request, PersonalInventoryItemDto.class);
      redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.created");
    } catch (Exception e) {
      log.error("Admin failed to create personal inventory item for {}", userSub, e);
      redirectAttributes.addFlashAttribute("errorToast", "personalInventory.error.create");
      redirectAttributes.addFlashAttribute("personalInventoryForm", form);
    }
    return redirectToList(userSub);
  }

  @PostMapping("/{userSub}/{id}/update")
  public String update(
      @PathVariable @NotNull String userSub,
      @PathVariable @NotNull UUID id,
      @Valid @ModelAttribute("personalInventoryForm") PersonalInventoryForm form,
      BindingResult bindingResult,
      RedirectAttributes redirectAttributes) {
    if (bindingResult.hasErrors()) {
      redirectAttributes.addFlashAttribute(
          "org.springframework.validation.BindingResult.personalInventoryForm", bindingResult);
      redirectAttributes.addFlashAttribute("personalInventoryForm", form);
      redirectAttributes.addFlashAttribute("showItemModal", true);
      redirectAttributes.addFlashAttribute(
          "modalAction", "/admin/personal-inventory/" + userSub + "/" + id + "/update");
      return redirectToList(userSub);
    }

    try {
      PersonalInventoryItemUpdateRequest request =
          new PersonalInventoryItemUpdateRequest(
              form.getName(),
              form.getNote(),
              form.getLocationUexId(),
              form.getLocationType(),
              form.getQuantity(),
              form.getVersion());
      backendApiClient.put(
          "/api/v1/admin/personal-inventory/" + id, request, PersonalInventoryItemDto.class);
      redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.updated");
    } catch (Exception e) {
      log.error("Admin failed to update personal inventory item {}", id, e);
      redirectAttributes.addFlashAttribute(
          "errorToast", classifyError(e, "personalInventory.error.update"));
    }
    return redirectToList(userSub);
  }

  @PostMapping("/{userSub}/{id}/delete")
  public String delete(
      @PathVariable @NotNull String userSub,
      @PathVariable @NotNull UUID id,
      RedirectAttributes redirectAttributes) {
    try {
      backendApiClient.delete("/api/v1/admin/personal-inventory/" + id, Void.class);
      redirectAttributes.addFlashAttribute("successToast", "personalInventory.toast.deleted");
    } catch (Exception e) {
      log.error("Admin failed to delete personal inventory item {}", id, e);
      redirectAttributes.addFlashAttribute(
          "errorToast", classifyError(e, "personalInventory.error.delete"));
    }
    return redirectToList(userSub);
  }

  private String redirectToList(String userSub) {
    return "redirect:/admin/personal-inventory?userSub="
        + URLEncoder.encode(userSub, StandardCharsets.UTF_8);
  }

  private List<UserDto> fetchUsers() {
    try {
      PageResponse<UserDto> result =
          backendApiClient.get("/api/v1/users?size=1000", new ParameterizedTypeReference<>() {});
      if (result == null || result.content() == null) {
        return Collections.emptyList();
      }
      List<UserDto> users = new ArrayList<>(result.content());
      users.sort(
          Comparator.comparing(
              u -> u.username() == null ? "" : u.username(), String.CASE_INSENSITIVE_ORDER));
      return users;
    } catch (Exception e) {
      log.error("Failed to fetch user list for admin personal inventory", e);
      return Collections.emptyList();
    }
  }

  private PageResponse<PersonalInventoryItemDto> fetchItems(
      String userSub, String q, Integer page, Integer size, String sort) {
    try {
      StringBuilder uri =
          new StringBuilder("/api/v1/admin/personal-inventory/")
              .append(URLEncoder.encode(userSub, StandardCharsets.UTF_8))
              .append('?');
      if (page != null) {
        uri.append("page=").append(page).append('&');
      }
      uri.append("size=").append(size == null ? 50 : size);
      if (sort != null && !sort.isBlank()) {
        uri.append("&sort=").append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
      }
      if (q != null && !q.isBlank()) {
        uri.append("&q=").append(URLEncoder.encode(q, StandardCharsets.UTF_8));
      }
      return backendApiClient.get(uri.toString(), new ParameterizedTypeReference<>() {});
    } catch (Exception e) {
      log.error("Failed to fetch personal inventory items for {}", userSub, e);
      return new PageResponse<>(new ArrayList<>(), 0, size == null ? 50 : size, 0, 0, List.of());
    }
  }

  private String classifyError(Exception e, String defaultKey) {
    if (e instanceof BackendServiceException bse && bse.getStatusCode() == 409) {
      return "personalInventory.error.conflict";
    }
    return defaultKey;
  }
}
