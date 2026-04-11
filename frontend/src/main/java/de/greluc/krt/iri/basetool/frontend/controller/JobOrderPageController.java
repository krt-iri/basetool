package de.greluc.krt.iri.basetool.frontend.controller;

import de.greluc.krt.iri.basetool.frontend.model.dto.*;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderForm;
import de.greluc.krt.iri.basetool.frontend.model.form.JobOrderHandoverForm;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class JobOrderPageController {

    private final BackendApiClient backendApiClient;
    private final RoleHierarchy roleHierarchy;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String viewOrders(
            @RequestParam(required = false) List<String> status,
            @CookieValue(name = "orders_filter_status", required = false) String cookieStatus,
            HttpServletResponse response,
            Model model) {
        if (status == null || status.isEmpty()) {
            if (cookieStatus != null && !cookieStatus.isBlank()) {
                status = Arrays.asList(cookieStatus.split("_"));
            } else {
                status = List.of("OPEN", "IN_PROGRESS");
            }
        } else {
            Cookie cookie = new Cookie("orders_filter_status", String.join("_", status));
            cookie.setPath("/orders");
            cookie.setMaxAge(30 * 24 * 60 * 60); // 30 days
            response.addCookie(cookie);
        }

        List<JobOrderDto> orders = new ArrayList<>();
        int yellowDays = 30;
        int redDays = 90;
        try {
            String statusParam = String.join(",", status);
            PageResponse<JobOrderDto> p = backendApiClient.get("/api/v1/orders?size=1000&sort=priority,asc&status=" + statusParam, new ParameterizedTypeReference<>() {});
            if (p != null && p.content() != null) {
                orders = new ArrayList<>(p.content());
                for (JobOrderDto order : orders) {
                    for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderMaterialDto mat : order.materials()) {
                        log.debug("Received stock for job order #{} ({}): {}/{} (material: {})", 
                            order.displayId(), order.id(), mat.currentStock(), mat.amount(), mat.material().name());
                    }
                }
            }
            
            try {
                SystemSettingDto yellowSetting = backendApiClient.get("/api/v1/settings/job_order.age_yellow_days", SystemSettingDto.class);
                yellowDays = Integer.parseInt(yellowSetting.value());
            } catch (Exception e) {
                log.warn("Could not fetch yellow days setting, using default");
            }
            try {
                SystemSettingDto redSetting = backendApiClient.get("/api/v1/settings/job_order.age_red_days", SystemSettingDto.class);
                redDays = Integer.parseInt(redSetting.value());
            } catch (Exception e) {
                log.warn("Could not fetch red days setting, using default");
            }
        } catch (Exception e) {
            log.error("Failed to fetch orders", e);
            log.error("Failed to load job orders", e);
            model.addAttribute("error", "error.joborder.load");
        }
        
        model.addAttribute("orders", orders);
        model.addAttribute("selectedStatuses", status);
        model.addAttribute("ageYellowDays", yellowDays);
        model.addAttribute("ageRedDays", redDays);
        return "orders-index";
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String viewOrderDetail(@PathVariable UUID id, Model model, @AuthenticationPrincipal OidcUser principal) {
        try {
            JobOrderDto order = backendApiClient.get("/api/v1/orders/" + id, JobOrderDto.class);
            model.addAttribute("order", order);
            model.addAttribute("currentUserId", getCurrentUserId(principal));
            
            boolean canAssign = isLogistician(principal);
            
            if (canAssign) {
                model.addAttribute("users", fetchUsers());
                model.addAttribute("materials", fetchMaterials());
                model.addAttribute("squadrons", fetchSquadrons());
                
                if (!model.containsAttribute("jobOrderForm")) {
                    JobOrderForm form = new JobOrderForm();
                    form.setSquadron(order.squadron());
                    form.setHandle(order.handle());
                    form.setVersion(order.version());
                    form.getMaterials().clear();
                    if (order.materials() != null) {
                        for (de.greluc.krt.iri.basetool.frontend.model.dto.JobOrderMaterialDto mat : order.materials()) {
                            JobOrderForm.JobOrderMaterialForm mf = new JobOrderForm.JobOrderMaterialForm();
                            mf.setMaterialId(mat.material().id());
                            mf.setMinQuality(mat.minQuality());
                            mf.setAmount(mat.amount());
                            form.getMaterials().add(mf);
                        }
                    }
                    if (form.getMaterials().isEmpty()) {
                        form.getMaterials().add(new JobOrderForm.JobOrderMaterialForm());
                    }
                    model.addAttribute("jobOrderForm", form);
                }
            } else {
                model.addAttribute("users", new ArrayList<>());
            }
            
            int yellowDays = 30;
            int redDays = 90;
            try {
                SystemSettingDto yellowSetting = backendApiClient.get("/api/v1/settings/job_order.age_yellow_days", SystemSettingDto.class);
                yellowDays = Integer.parseInt(yellowSetting.value());
            } catch (Exception e) {
                log.warn("Could not fetch yellow days setting, using default");
            }
            try {
                SystemSettingDto redSetting = backendApiClient.get("/api/v1/settings/job_order.age_red_days", SystemSettingDto.class);
                redDays = Integer.parseInt(redSetting.value());
            } catch (Exception e) {
                log.warn("Could not fetch red days setting, using default");
            }
            model.addAttribute("ageYellowDays", yellowDays);
            model.addAttribute("ageRedDays", redDays);

            if (!model.containsAttribute("handoverForm")) {
                JobOrderHandoverForm handoverForm = new JobOrderHandoverForm();
                handoverForm.setHandoverTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
                handoverForm.setRecipientSquadron(order.squadron());
                model.addAttribute("handoverForm", handoverForm);
            }
        } catch (Exception e) {
            log.error("Failed to fetch order", e);
            log.error("Failed to load job order", e);
            model.addAttribute("error", "error.joborder.load.details");
            return "redirect:/orders";
        }
        return "orders-detail";
    }

    @GetMapping("/create")
    public String viewCreateForm(@RequestParam(required = false) String source, Model model) {
        if (!model.containsAttribute("jobOrderForm")) {
            JobOrderForm form = new JobOrderForm();
            form.setSource(source);
            model.addAttribute("jobOrderForm", form);
        } else {
            JobOrderForm form = (JobOrderForm) model.getAttribute("jobOrderForm");
            if (form != null && form.getSource() == null) {
                form.setSource(source);
            }
        }
        model.addAttribute("materials", fetchMaterials());
        model.addAttribute("squadrons", fetchSquadrons());
        return "orders-create";
    }

    @PostMapping("/create")
    public String createOrder(@ModelAttribute("jobOrderForm") JobOrderForm form, RedirectAttributes redirectAttributes, @AuthenticationPrincipal OidcUser principal) {
        try {
            List<CreateJobOrderMaterialDto> materials = form.getMaterials().stream()
                    .filter(m -> m.getMaterialId() != null && m.getAmount() != null && m.getAmount() > 0)
                    .map(m -> new CreateJobOrderMaterialDto(m.getMaterialId(), m.getMinQuality(), m.getAmount()))
                    .collect(Collectors.toList());

            if (materials.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
                redirectAttributes.addFlashAttribute("jobOrderForm", form);
                return "redirect:/orders/create" + (form.getSource() != null ? "?source=" + form.getSource() : "");
            }

            CreateJobOrderDto dto = new CreateJobOrderDto(form.getSquadron(), form.getHandle(), materials, form.getVersion());
            backendApiClient.post("/api/v1/orders", dto, JobOrderDto.class, true);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.create");
            
            if (principal == null) {
                return "redirect:/orders/create" + (form.getSource() != null ? "?source=" + form.getSource() : "");
            }
            if ("index".equals(form.getSource())) {
                return "redirect:/orders";
            }
            // fallback
            return "redirect:/orders";
        } catch (Exception e) {
            log.error("Failed to create order", e);
            log.error("Failed to create job order", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.create.failed");
            redirectAttributes.addFlashAttribute("jobOrderForm", form);
            return "redirect:/orders/create" + (form.getSource() != null ? "?source=" + form.getSource() : "");
        }
    }

    @PostMapping("/{id}/priority")
    @PreAuthorize("isAuthenticated()")
    public String updatePriority(@PathVariable UUID id, @RequestParam Integer priority, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.put("/api/v1/orders/" + id + "/priority?priority=" + priority, null, JobOrderDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.priority");
        } catch (Exception e) {
            log.error("Failed to update priority", e);
            log.error("Failed to update job order priority", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.priority.failed");
        }
        return "redirect:/orders";
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public String updateStatus(@PathVariable UUID id, @RequestParam String status, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.put("/api/v1/orders/" + id + "/status?status=" + status, null, JobOrderDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.status");
        } catch (Exception e) {
            log.error("Failed to update status", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.status.failed");
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/update")
    @PreAuthorize("hasRole('LOGISTICIAN')")
    public String updateOrder(@PathVariable UUID id, @ModelAttribute("jobOrderForm") JobOrderForm form, RedirectAttributes redirectAttributes) {
        try {
            List<CreateJobOrderMaterialDto> materials = form.getMaterials().stream()
                    .filter(m -> m.getMaterialId() != null && m.getAmount() != null && m.getAmount() > 0)
                    .map(m -> new CreateJobOrderMaterialDto(m.getMaterialId(), m.getMinQuality(), m.getAmount()))
                    .collect(Collectors.toList());

            if (materials.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorToast", "error.joborder.material.invalid");
                redirectAttributes.addFlashAttribute("jobOrderForm", form);
                return "redirect:/orders/" + id;
            }

            CreateJobOrderDto dto = new CreateJobOrderDto(form.getSquadron(), form.getHandle(), materials, form.getVersion());
            backendApiClient.put("/api/v1/orders/" + id, dto, JobOrderDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.update");
            return "redirect:/orders/" + id;
        } catch (Exception e) {
            log.error("Failed to update order", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.update.failed");
            redirectAttributes.addFlashAttribute("jobOrderForm", form);
            return "redirect:/orders/" + id;
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated()")
    public String deleteOrder(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/orders/" + id, Void.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.delete");
        } catch (Exception e) {
            log.error("Failed to delete order", e);
            log.error("Failed to delete job order", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.delete.failed");
        }
        return "redirect:/orders";
    }

    @PostMapping("/{id}/assignees")
    @PreAuthorize("isAuthenticated()")
    public String addAssignee(@PathVariable UUID id, @RequestParam UUID userId, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.post("/api/v1/orders/" + id + "/assignees/" + userId, null, JobOrderDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.assignee.added");
        } catch (Exception e) {
            log.error("Failed to add assignee", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.assignee.add");
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/handovers")
    @PreAuthorize("hasRole('LOGISTICIAN') or hasRole('OFFICER') or hasRole('ADMIN')")
    public String createHandover(@PathVariable UUID id, @ModelAttribute("handoverForm") JobOrderHandoverForm form, RedirectAttributes redirectAttributes) {
        try {
            List<JobOrderHandoverItemCreateDto> items = form.getItems().stream()
                    .filter(item -> item.getInventoryItemId() != null && item.getAmount() != null && item.getAmount() > 0)
                    .map(item -> new JobOrderHandoverItemCreateDto(item.getInventoryItemId(), item.getAmount()))
                    .collect(Collectors.toList());

            if (items.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.noitems");
                return "redirect:/orders/" + id;
            }

            Instant handoverTime = Instant.now();
            if (form.getHandoverTime() != null && !form.getHandoverTime().isBlank()) {
                try {
                    handoverTime = LocalDateTime.parse(form.getHandoverTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            .atZone(ZoneId.systemDefault()).toInstant();
                } catch (Exception e) {
                    log.warn("Could not parse handoverTime {}, using now()", form.getHandoverTime());
                }
            }

            JobOrderHandoverCreateDto dto = new JobOrderHandoverCreateDto(
                    handoverTime,
                    form.getRecipientHandle(),
                    form.getRecipientSquadron(),
                    items
            );

            backendApiClient.post("/api/v1/orders/" + id + "/handovers", dto, JobOrderHandoverDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.handover");
        } catch (Exception e) {
            log.error("Failed to create handover", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.handover.failed");
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/assignees/remove")
    @PreAuthorize("isAuthenticated()")
    public String removeAssignee(@PathVariable UUID id, @RequestParam UUID userId, RedirectAttributes redirectAttributes) {
        try {
            backendApiClient.delete("/api/v1/orders/" + id + "/assignees/" + userId, JobOrderDto.class);
            redirectAttributes.addFlashAttribute("successToast", "success.joborder.assignee.removed");
        } catch (Exception e) {
            log.error("Failed to remove assignee", e);
            redirectAttributes.addFlashAttribute("errorToast", "error.joborder.assignee.remove");
        }
        return "redirect:/orders/" + id;
    }

    @GetMapping("/{id}/materials/{matId}/inventory")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public List<InventoryItemDto> getInventoryItemsForMaterial(
            @PathVariable UUID id, @PathVariable UUID matId) {
        try {
            return backendApiClient.get("/api/v1/orders/" + id + "/materials/" + matId + "/inventory", new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to fetch inventory items for job order {} and material {}", id, matId, e);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load inventory items");
        }
    }

    private List<UserDto> fetchUsers() {
        try {
            PageResponse<UserDto> p = backendApiClient.get("/api/v1/users?size=1000", new ParameterizedTypeReference<PageResponse<UserDto>>() {});
            if (p != null && p.content() != null) {
                return new ArrayList<>(p.content());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch users (might not be an admin/officer)");
        }
        return new ArrayList<>();
    }
    
    private List<MaterialDto> fetchMaterials() {
        try {
            PageResponse<MaterialDto> p = backendApiClient.getCached("/api/v1/materials?size=1000", new ParameterizedTypeReference<>() {}, true);
            if (p != null && p.content() != null) {
                return new ArrayList<>(p.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch materials", e);
        }
        return new ArrayList<>();
    }

    private List<SquadronDto> fetchSquadrons() {
        try {
            PageResponse<SquadronDto> p = backendApiClient.getCached("/api/v1/squadrons?size=1000&sort=name,asc", new ParameterizedTypeReference<>() {}, true);
            if (p != null && p.content() != null) {
                return new ArrayList<>(p.content());
            }
        } catch (Exception e) {
            log.error("Failed to fetch squadrons", e);
        }
        return new ArrayList<>();
    }

    private UUID getCurrentUserId(OidcUser principal) {
        if (principal == null) return null;
        try {
            return UUID.fromString(principal.getSubject());
        } catch (Exception e) {
            try {
                UserDto me = backendApiClient.get("/api/v1/users/me", UserDto.class);
                return me != null ? me.id() : null;
            } catch (Exception ex) {
                log.warn("Failed to get current user ID from backend: {}", ex.getMessage());
                return null;
            }
        }
    }

    private boolean isLogistician(OidcUser principal) {
        if (principal == null) return false;
        
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = (auth != null) ? auth.getAuthorities() : principal.getAuthorities();
        
        Collection<? extends GrantedAuthority> reachableAuthorities = roleHierarchy.getReachableGrantedAuthorities(authorities);
        log.info("[DEBUG_LOG] JobOrder: Checking logistician status for user {}. Original authorities: {}. Reachable authorities: {}", 
                principal.getName(), authorities, reachableAuthorities);
        boolean result = reachableAuthorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN") ||
                               a.getAuthority().equals("ROLE_ADMIN") ||
                               a.getAuthority().equals("ROLE_OFFICER"));
        log.info("[DEBUG_LOG] JobOrder: Is logistician: {}", result);
        return result;
    }
}
