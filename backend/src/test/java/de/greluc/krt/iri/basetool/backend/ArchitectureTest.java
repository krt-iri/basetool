package de.greluc.krt.iri.basetool.backend;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * ArchUnit tests that mechanically enforce the architectural invariants from CLAUDE.md.
 *
 * <p>Each rule below corresponds to a bullet in the project guide:
 * <ul>
 *   <li>"Authorization is centralized in {@code @PreAuthorize} annotations on services/controllers
 *       — keep checks out of business logic." → {@link #serviceLayerShouldNotReachIntoSecurityContext()}
 *       (no {@code SecurityContextHolder} outside the {@code UserService} auth helper) plus
 *       {@link #everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation()}.</li>
 *   <li>"DTOs only at boundaries. Never expose JPA entities at controller boundaries." →
 *       {@link #controllerMethodsShouldNotReturnJpaEntities()}.</li>
 * </ul>
 *
 * <p>These rules are static checks against the imported bytecode under
 * {@code de.greluc.krt.iri.basetool.backend.*}; tests on the test classpath are excluded
 * so the rules describe the production-code contract only.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.greluc.krt.iri.basetool.backend");

    private static final String SECURITY_CONTEXT_HOLDER =
            "org.springframework.security.core.context.SecurityContextHolder";

    private static final String PRE_AUTHORIZE =
            "org.springframework.security.access.prepost.PreAuthorize";

    private static final String JPA_ENTITY =
            "jakarta.persistence.Entity";

    /**
     * Classes that are allowed to reach into {@link org.springframework.security.core.context.SecurityContextHolder}
     * despite being on the service-layer package. The list is intentionally short and
     * each entry is justified inline.
     */
    private static final java.util.Set<String> SECURITY_CONTEXT_HOLDER_EXCEPTIONS = java.util.Set.of(
            // Legitimate auth-helper that the rest of the codebase calls into instead of
            // touching SecurityContextHolder directly. This IS the seam.
            "de.greluc.krt.iri.basetool.backend.service.UserService"
    );

    @Test
    void serviceLayerShouldNotReachIntoSecurityContext() {
        // Reasoning: business logic in the service layer must rely on the @PreAuthorize
        // boundary at the controller (or the service method itself, when the rule is
        // role-based) instead of pulling the JWT subject straight from
        // SecurityContextHolder. Otherwise the same service method behaves differently
        // depending on which thread invokes it (Spring scheduling, async, message
        // listeners) and the data-isolation rules become testable only through full
        // Spring context tests. The narrow allowed escape valve is the existing
        // `UserService.getCurrentUser()` helper in the auth layer; nothing else inside
        // the business-service package should bypass that.
        noClasses()
                .that().resideInAPackage("..backend.service..")
                .and().haveNameNotMatching(allowedClassNamesRegex())
                .should().dependOnClassesThat().haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
                .because("Business services must not pull the authenticated principal directly; "
                        + "use a controller-side @PreAuthorize check or inject UserService instead. "
                        + "The allow-list lives at the top of this test file.")
                .check(CLASSES);
    }

    @Test
    void controllerMethodsShouldNotReturnJpaEntities() {
        // Reasoning: CLAUDE.md is explicit — "Never expose JPA entities at controller
        // boundaries." A leaked entity drags in Hibernate lazy-loading semantics across
        // the HTTP boundary (Jackson serialising a proxy triggers the famous
        // LazyInitializationException) AND can expose internal columns to the client.
        // The check is intentionally narrow: ArchUnit can only inspect the *raw* return
        // type, so `ResponseEntity<User>` would not be caught by `haveRawReturnType(...)`.
        // In this codebase entity-returning controller methods would still be visible
        // here because the convention is to return the entity directly, not wrap it.
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..backend.controller..")
                .and().arePublic()
                .should().haveRawReturnType(annotatedWith(JPA_ENTITY))
                .because("Controllers must return DTOs (or Page<Dto>/ResponseEntity<Dto>), never raw JPA entities.")
                .check(CLASSES);
    }

    @Test
    void everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation() {
        // Reasoning: every @RestController must make at least one explicit authorisation
        // decision somewhere — either a class-level @PreAuthorize or at least one
        // method-level @PreAuthorize. The weaker form (class-level) is sufficient to
        // catch the worst regression case: a controller that ships with zero auth
        // annotations and silently falls through to SecurityConfig's catch-all.
        //
        // We deliberately do NOT require every handler method to be annotated, because
        // the current codebase mixes the two patterns ("controller-level @PreAuthorize
        // covers everything" vs. "method-level @PreAuthorize per endpoint") and many
        // public endpoints are gated by SecurityConfig's `requestMatchers(...).permitAll()`
        // instead. Tightening to per-method is a separate, larger follow-up.
        classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(haveAtLeastOnePreAuthorizeAnnotation())
                .because("Every REST controller class must declare at least one @PreAuthorize "
                        + "annotation (either on the class or on any method) so it cannot silently "
                        + "bypass authorisation. Public endpoints should use @PreAuthorize(\"permitAll()\").")
                .check(CLASSES);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static String allowedClassNamesRegex() {
        return SECURITY_CONTEXT_HOLDER_EXCEPTIONS.stream()
                .map(java.util.regex.Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow();
    }

    private static ArchCondition<JavaClass> haveAtLeastOnePreAuthorizeAnnotation() {
        return new ArchCondition<JavaClass>("declare @PreAuthorize on the class or on at least one method") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (clazz.isAnnotatedWith(PRE_AUTHORIZE)) {
                    return;
                }
                for (JavaMethod method : clazz.getMethods()) {
                    if (method.isAnnotatedWith(PRE_AUTHORIZE)) {
                        return;
                    }
                }
                events.add(SimpleConditionEvent.violated(clazz,
                        clazz.getFullName() + " is a @RestController but declares no @PreAuthorize "
                                + "annotation on the class or on any of its methods"));
            }
        };
    }

    private static DescribedPredicate<JavaClass> annotatedWith(String annotationFqn) {
        return new DescribedPredicate<JavaClass>("annotated with @" + annotationFqn) {
            @Override
            public boolean test(JavaClass clazz) {
                return clazz.isAnnotatedWith(annotationFqn);
            }
        };
    }
}
