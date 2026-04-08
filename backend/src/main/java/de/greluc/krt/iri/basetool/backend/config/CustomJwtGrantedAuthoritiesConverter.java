package de.greluc.krt.iri.basetool.backend.config;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final UserService userService;

    @Override
    public Collection<GrantedAuthority> convert(@NonNull Jwt jwt) {
        int attempts = 3;
        while (attempts > 0) {
            try {
                User user = userService.syncUser(jwt);
                
                Collection<GrantedAuthority> authorities = user.getRoles().stream()
                    .flatMap(role -> {
                        // Add ROLE_NAME
                        Stream<GrantedAuthority> roleAuth = Stream.of(new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase().replace(" ", "_")));
                        
                        // Add PERMISSIONS
                        Stream<GrantedAuthority> permAuth = role.getPermissions().stream()
                            .map(SimpleGrantedAuthority::new);
                            
                        return Stream.concat(roleAuth, permAuth);
                    })
                    .collect(Collectors.toCollection(ArrayList::new));

                if (user.isLogistician()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_LOGISTICIAN"));
                }
                
                if (user.isMissionManager()) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER"));
                }
                
                return authorities;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                attempts--;
                log.warn("Optimistic locking failure during user sync. Attempts left: {}", attempts);
                if (attempts == 0) {
                     return fallbackToJwtRoles(jwt, e);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                return fallbackToJwtRoles(jwt, e);
            }
        }
        return new ArrayList<>();
    }

    private Collection<GrantedAuthority> fallbackToJwtRoles(Jwt jwt, Exception e) {
        log.error("Failed to sync user from JWT. Falling back to JWT roles. Error: {}", e.getMessage());
        return userService.extractRolesFromJwt(jwt).stream()
                .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase().replace(" ", "_")))
                .collect(Collectors.toList());
    }
}
