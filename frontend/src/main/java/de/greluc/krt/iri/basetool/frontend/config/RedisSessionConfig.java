package de.greluc.krt.iri.basetool.frontend.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Configures Spring Session with a Jackson 3 based JSON serializer for Redis.
 *
 * <p>By default, Spring Session uses Java serialization for storing session objects in Redis.
 * This fails silently for OAuth2/OIDC types ({@code OidcUser}, {@code OAuth2AuthorizedClient},
 * etc.) in Spring Security 7.x / Spring Boot 4.x, resulting in an empty Redis store despite
 * a successful login — the session is never actually persisted.
 *
 * <p>This configuration switches to Jackson 3 JSON serialization using
 * {@link GenericJacksonJsonRedisSerializer} with all required Spring Security and OAuth2 modules
 * registered via {@link SecurityJacksonModules}. This ensures that the full authentication
 * context (including {@code OidcUser}, {@code OAuth2AuthorizedClient}, and tokens) is correctly
 * serialized to and deserialized from Redis across frontend restarts.
 *
 * <p>Uses {@code @EnableRedisIndexedHttpSession} to enable Redis-backed HTTP sessions with index
 * support for session lookup by principal name and session ID.
 *
 * <p><b>Session Timeout:</b> {@code @EnableRedisIndexedHttpSession} disables Spring Boot's
 * auto-configuration bridge that would normally apply {@code server.servlet.session.timeout} to
 * the {@link RedisIndexedSessionRepository}. Without explicit configuration, the default of
 * 1800 seconds (30 minutes) is used. A {@link SessionRepositoryCustomizer} bean re-applies the
 * configured timeout value so that Redis sessions honour the same TTL as configured in
 * {@code application.yml}.
 *
 * <p>This configuration is excluded from the {@code test} profile to prevent Redis connection
 * attempts during unit and integration tests.
 */
@Configuration
@EnableRedisIndexedHttpSession
@Profile("!test")
public class RedisSessionConfig {

    /**
     * Session timeout read from {@code server.servlet.session.timeout} (default: 240h).
     *
     * <p>{@code @EnableRedisIndexedHttpSession} disables Spring Boot's auto-configuration for
     * Spring Session, so {@code server.servlet.session.timeout} is NOT automatically applied
     * to the {@link RedisIndexedSessionRepository}. Without explicit configuration the default
     * of 1800 seconds (30 minutes) is used — sessions expire far too quickly.
     * This field is injected here and applied via {@link #sessionRepositoryCustomizer()}.
     */
    @Value("${server.servlet.session.timeout:240h}")
    private Duration sessionTimeout;

    /**
     * Redis key namespace read from {@code spring.session.redis.namespace} (default: {@code basetool:session}).
     *
     * <p>{@code @EnableRedisIndexedHttpSession} bypasses Spring Boot's auto-configuration, so
     * {@code spring.session.redis.namespace} from {@code application.yml} is NOT automatically
     * applied to the {@link RedisIndexedSessionRepository}. Without explicit configuration the
     * default namespace {@code spring:session} is used — sessions are stored under
     * {@code spring:session:*} keys, not under the configured {@code basetool:session:*} keys.
     * This field is injected here and applied via {@link #sessionRepositoryCustomizer()}.
     */
    @Value("${spring.session.redis.namespace:basetool:session}")
    private String redisNamespace;

    /**
     * Provides a Jackson 3 based {@link RedisSerializer} for Spring Session.
     *
     * <p>Uses {@link SecurityJacksonModules#getModules(ClassLoader)} which automatically
     * registers all Spring Security modules found on the classpath, including:
     * <ul>
     *   <li>{@code CoreJacksonModule} — SecurityContext, Authentication, GrantedAuthority</li>
     *   <li>{@code OAuth2ClientJacksonModule} — OidcUser, OAuth2AuthorizedClient, tokens</li>
     *   <li>{@code WebJacksonModule}, {@code WebServletJacksonModule} — web types</li>
     * </ul>
     *
     * <p>A custom {@link BasicPolymorphicTypeValidator} with {@code allowIfBaseType(Object.class)}
     * permits any class that is a subtype of {@code Object} (effectively all Java classes).
     * This is required because Spring Session serializes heterogeneous types — {@code Long},
     * {@code HashMap}, {@code Instant}, {@code OidcUser}, {@code OAuth2AuthorizedClient}, etc.
     * In Jackson 3, {@code allowIfSubType(String)} does NOT match by name prefix; it checks
     * class hierarchy. Therefore, package-prefix strings like {@code "java.lang."} do NOT match
     * final classes such as {@code Long}. The {@code allowIfBaseType(Object.class)} approach
     * is safe because session data originates only from our own application and Keycloak.
     * The builder is passed to
     * {@link SecurityJacksonModules#getModules(ClassLoader, BasicPolymorphicTypeValidator.Builder)}
     * which extends it with all required Spring Security type allowances.
     *
     * @return the configured {@link RedisSerializer} for session data
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // Build a Jackson 3 JsonMapper with all Spring Security + OAuth2 modules.
        // SecurityJacksonModules.getModules() automatically includes CoreJacksonModule,
        // OAuth2ClientJacksonModule (OidcUser, tokens, etc.), WebJacksonModule, and others.
        //
        // IMPORTANT — Jackson 3 type validator behavior:
        // In Jackson 3, BasicPolymorphicTypeValidator.allowIfSubType(String) checks whether the
        // candidate class is a subtype of a class/interface with that exact fully-qualified name.
        // It does NOT perform prefix/package matching on the class name string.
        // Therefore, allowIfSubType("java.lang.") does NOT match java.lang.Long (a final class
        // with no superclass named "java.lang.").
        //
        // The correct approach for Spring Session, which serializes many heterogeneous types
        // (Long, HashMap, Instant, OidcUser, OAuth2AuthorizedClient, etc.), is to use
        // allowIfBaseType(Object.class): this permits any class that is a subtype of Object,
        // which is effectively all Java classes. This is the minimal safe option for session
        // serialization because the session data is trusted (it originates from our own application
        // and Keycloak's OAuth2 flow — not from user-controlled input).
        BasicPolymorphicTypeValidator.Builder typeValidatorBuilder = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class);

        ClassLoader loader = getClass().getClassLoader();
        JsonMapper mapper = JsonMapper.builder()
            .addModules(SecurityJacksonModules.getModules(loader, typeValidatorBuilder))
            .build();

        return new GenericJacksonJsonRedisSerializer(mapper);
    }

    /**
     * Applies the configured session timeout, namespace, and flush mode to the
     * {@link RedisIndexedSessionRepository}.
     *
     * <p>When using {@code @EnableRedisIndexedHttpSession}, Spring Boot's auto-configuration
     * bridge is bypassed and none of the following properties are applied automatically:
     * <ul>
     *   <li>{@code server.servlet.session.timeout} → default 1800s (30 min) instead of configured 240h</li>
     *   <li>{@code spring.session.redis.namespace} → default {@code spring:session} instead of
     *       configured {@code basetool:session}; causes {@code keys "basetool:session:*"} to return
     *       empty even when sessions are correctly persisted</li>
     *   <li>{@code spring.session.redis.flush-mode} → default {@code ON_SAVE} instead of
     *       {@code IMMEDIATE}; sessions may not be written to Redis before a restart</li>
     * </ul>
     * This customizer explicitly re-applies all three settings.
     *
     * @return a customizer that sets timeout, namespace, and flush mode on the repository
     */
    @Bean
    public SessionRepositoryCustomizer<RedisIndexedSessionRepository> sessionRepositoryCustomizer() {
        return repository -> {
            repository.setDefaultMaxInactiveInterval(sessionTimeout);
            repository.setRedisKeyNamespace(redisNamespace);
            repository.setFlushMode(FlushMode.IMMEDIATE);
        };
    }

    /**
     * Stores {@code OAuth2AuthorizedClient} (access token, refresh token, client registration)
     * in the HTTP session (backed by Redis) instead of the default in-memory store.
     *
     * <p>This ensures that OAuth2 tokens survive frontend restarts: the tokens are persisted
     * in Redis alongside the session and restored transparently on the next request, enabling
     * automatic token refresh without user interaction.
     *
     * <p>Placed here (not in {@link SecurityConfig}) to avoid a circular bean dependency:
     * {@code SecurityConfig} → {@code BackendRoleSyncFilter} → {@code BackendApiClient}
     * → {@code WebClient} → {@code authorizedClientManager} → {@code OAuth2AuthorizedClientRepository}
     * → {@code SecurityConfig}.
     *
     * @return the session-backed {@link OAuth2AuthorizedClientRepository}
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }
}
