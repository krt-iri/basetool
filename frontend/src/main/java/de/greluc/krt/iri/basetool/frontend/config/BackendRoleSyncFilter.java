package de.greluc.krt.iri.basetool.frontend.config;

import de.greluc.krt.iri.basetool.frontend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet filter handling Backend Role Sync. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackendRoleSyncFilter extends OncePerRequestFilter {

  private final BackendApiClient backendApiClient;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();
  private static final String SYNC_COMPLETE_FLAG = "BACKEND_ROLES_SYNCED";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    if (auth != null && auth.isAuthenticated() && auth instanceof OAuth2AuthenticationToken token) {
      HttpSession session = request.getSession(false);
      if (session != null && session.getAttribute(SYNC_COMPLETE_FLAG) == null) {
        log.debug("Session exists, starting role sync for user: {}", token.getName());
        syncRoles(token, request, response);
        session.setAttribute(SYNC_COMPLETE_FLAG, true);
      }
    }

    filterChain.doFilter(request, response);
  }

  private void syncRoles(
      OAuth2AuthenticationToken token, HttpServletRequest request, HttpServletResponse response) {
    try {
      log.debug("Syncing backend roles for user: {}", token.getName());
      UserDto user = backendApiClient.get("/api/v1/users/me", UserDto.class);

      if (user != null) {
        log.info("[DEBUG_LOG] User roles from backend: {}", user.roles());
        List<GrantedAuthority> updatedAuthorities = new ArrayList<>(token.getAuthorities());
        boolean modified = false;

        // Sync roles from backend database
        if (user.roles() != null) {
          for (String roleName : user.roles()) {
            String formattedRole = "ROLE_" + roleName.toUpperCase().replace(" ", "_");
            if (updatedAuthorities.stream()
                .noneMatch(a -> a.getAuthority().equals(formattedRole))) {
              log.info(
                  "[DEBUG_LOG] Adding {} from backend to user: {}", formattedRole, token.getName());
              updatedAuthorities.add(new SimpleGrantedAuthority(formattedRole));
              modified = true;
            }
          }
        }

        // Sync permissions from backend database
        if (user.permissions() != null) {
          for (String permission : user.permissions()) {
            if (updatedAuthorities.stream().noneMatch(a -> a.getAuthority().equals(permission))) {
              log.debug(
                  "Adding permission {} from backend to user: {}", permission, token.getName());
              updatedAuthorities.add(new SimpleGrantedAuthority(permission));
              modified = true;
            }
          }
        }

        // Sync special flags
        if (Boolean.TRUE.equals(user.isLogistician())
            && updatedAuthorities.stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_LOGISTICIAN"))) {
          log.info("Adding ROLE_LOGISTICIAN from backend to user: {}", token.getName());
          updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"));
          modified = true;
        }

        if (Boolean.TRUE.equals(user.isMissionManager())
            && updatedAuthorities.stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_MISSION_MANAGER"))) {
          log.info("Adding ROLE_MISSION_MANAGER from backend to user: {}", token.getName());
          updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"));
          modified = true;
        }

        if (modified) {
          OAuth2AuthenticationToken newAuth;
          if (token.getPrincipal() instanceof OidcUser oidcUser) {
            // We must preserve the nameAttributeKey to avoid changing the principal name,
            // which would break OAuth2AuthorizedClient lookups.
            String nameAttributeKey = "sub"; // Default
            String currentName = oidcUser.getName();

            if (currentName != null) {
              if (currentName.equals(oidcUser.getPreferredUsername())) {
                nameAttributeKey = "preferred_username";
              } else if (currentName.equals(oidcUser.getEmail())) {
                nameAttributeKey = "email";
              } else {
                // Search for the key that matches the current name
                for (java.util.Map.Entry<String, Object> entry :
                    oidcUser.getAttributes().entrySet()) {
                  if (currentName.equals(String.valueOf(entry.getValue()))) {
                    nameAttributeKey = entry.getKey();
                    break;
                  }
                }
              }
            }

            log.debug(
                "Using nameAttributeKey: {} for new OidcUser (current name: {})",
                nameAttributeKey,
                currentName);
            OidcUser newPrincipal =
                new DefaultOidcUser(
                    updatedAuthorities,
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo(),
                    nameAttributeKey);

            if (!newPrincipal.getName().equals(currentName)) {
              log.warn(
                  "Principal name changed during sync! Old: {}, New: {}. This may break OAuth2"
                      + " lookups.",
                  currentName,
                  newPrincipal.getName());
            }

            newAuth =
                new OAuth2AuthenticationToken(
                    newPrincipal, updatedAuthorities, token.getAuthorizedClientRegistrationId());
          } else {
            newAuth =
                new OAuth2AuthenticationToken(
                    token.getPrincipal(),
                    updatedAuthorities,
                    token.getAuthorizedClientRegistrationId());
          }

          newAuth.setDetails(token.getDetails());
          log.info(
              "Replaced Authentication in SecurityContext for user: {} (New name: {})",
              token.getName(),
              newAuth.getName());

          org.springframework.security.core.context.SecurityContext context =
              SecurityContextHolder.getContext();
          context.setAuthentication(newAuth);
          securityContextRepository.saveContext(context, request, response);
        } else {
          log.debug("No new roles to add for user: {}", token.getName());
        }
      }
    } catch (Exception e) {
      log.error("Failed to sync backend roles for user: {}", token.getName(), e);
    }
  }
}
