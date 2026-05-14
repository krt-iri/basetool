package de.greluc.krt.iri.basetool.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that mechanically enforce the architectural invariants from CLAUDE.md.
 *
 * <p>Each rule below corresponds to a bullet in the project guide:
 *
 * <ul>
 *   <li>"Authorization is centralized in {@code @PreAuthorize} annotations on services/controllers
 *       — keep checks out of business logic." → {@link
 *       #serviceLayerShouldNotReachIntoSecurityContext()}, {@link
 *       #controllerLayerShouldNotReachIntoSecurityContext()} and {@link
 *       #mapperLayerShouldNotReachIntoSecurityContext()} (no {@code SecurityContextHolder} outside
 *       the dedicated auth-helper services) plus {@link
 *       #everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation()}.
 *   <li>"DTOs only at boundaries. Never expose JPA entities at controller boundaries." → {@link
 *       #controllerMethodsShouldNotReturnJpaEntities()}.
 * </ul>
 *
 * <p>These rules are static checks against the imported bytecode under {@code
 * de.greluc.krt.iri.basetool.backend.*}; tests on the test classpath are excluded so the rules
 * describe the production-code contract only.
 */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("de.greluc.krt.iri.basetool.backend");

  private static final String SECURITY_CONTEXT_HOLDER =
      "org.springframework.security.core.context.SecurityContextHolder";

  private static final String PRE_AUTHORIZE =
      "org.springframework.security.access.prepost.PreAuthorize";

  private static final String JPA_ENTITY = "jakarta.persistence.Entity";

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
  private static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
  private static final String DELETE_MAPPING =
      "org.springframework.web.bind.annotation.DeleteMapping";
  private static final String PATCH_MAPPING =
      "org.springframework.web.bind.annotation.PatchMapping";

  /**
   * Java-generic wrappers that controllers legitimately return (paging envelopes, optional results,
   * response wrappers). The Entity-Generic rule below scans the actual type arguments of these
   * wrappers to make sure a JPA {@code @Entity} never leaks through.
   */
  private static final Set<String> ENTITY_GENERIC_WRAPPERS =
      Set.of(
          "org.springframework.http.ResponseEntity",
          "org.springframework.data.domain.Page",
          "org.springframework.data.domain.Slice",
          "java.util.List",
          "java.util.Set",
          "java.util.Collection",
          "java.util.Optional",
          "java.lang.Iterable");

  /**
   * Method-name prefixes that the codebase uses for state-mutating service operations. Used by
   * {@link #mutatingServiceMethodsInReadOnlyClassesNeedExplicitTransactional()} to find methods
   * that must override a class-level {@code @Transactional(readOnly = true)} with their own
   * {@code @Transactional}. The list is conservative — anything that does NOT start with one of
   * these prefixes is treated as a read operation.
   */
  private static final Set<String> MUTATING_METHOD_PREFIXES =
      Set.of(
          "create",
          "update",
          "delete",
          "add",
          "remove",
          "save",
          "store",
          "book",
          "handover",
          "link",
          "unlink",
          "move",
          "reset",
          "patch",
          "complete",
          "approve",
          "reject",
          "publish",
          "cancel",
          "join",
          "leave",
          "register",
          "unregister",
          "set",
          "insert",
          "merge",
          "assign",
          "unassign",
          "increment",
          "decrement",
          "clear",
          "purge",
          "import",
          "sync");

  /**
   * Classes that are allowed to reach into {@link
   * org.springframework.security.core.context.SecurityContextHolder} despite being on the
   * service-layer package. By design the list contains exactly one entry — {@code
   * AuthHelperService} — so there is a single source of truth for "what is the current
   * authentication" across the codebase.
   */
  private static final java.util.Set<String> SECURITY_CONTEXT_HOLDER_EXCEPTIONS =
      java.util.Set.of(
          // The dedicated auth-helper service. Centralises the SecurityContextHolder
          // read so the rest of the codebase can consult the current authentication
          // through a constructor-injected dependency. THIS is the seam — every other
          // service/controller/mapper must depend on AuthHelperService instead of
          // touching SecurityContextHolder directly.
          "de.greluc.krt.iri.basetool.backend.service.AuthHelperService");

  @Test
  void serviceLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: business logic in the service layer must rely on the @PreAuthorize
    // boundary at the controller (or the service method itself, when the rule is
    // role-based) instead of pulling the JWT subject straight from
    // SecurityContextHolder. Otherwise the same service method behaves differently
    // depending on which thread invokes it (Spring scheduling, async, message
    // listeners) and the data-isolation rules become testable only through full
    // Spring context tests. The single allowed escape valve is AuthHelperService;
    // nothing else inside the business-service package should bypass that.
    noClasses()
        .that()
        .resideInAPackage("..backend.service..")
        .and()
        .haveNameNotMatching(allowedClassNamesRegex())
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Business services must not pull the authenticated principal directly; "
                + "use a controller-side @PreAuthorize check or inject AuthHelperService instead. "
                + "The allow-list lives at the top of this test file.")
        .check(CLASSES);
  }

  @Test
  void controllerLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: same rationale as serviceLayerShouldNotReachIntoSecurityContext —
    // controllers used to inline role-hierarchy checks via
    // `SecurityContextHolder.getContext().getAuthentication()` (see
    // JobOrderController#verifyAssigneeAccess, InventoryItemController#isLogisticianOrAbove
    // and RefineryOrderController#isLogisticianOrAbove before the refactor). The rule
    // now forbids that pattern across the controller package; controllers must
    // either accept the authentication as a method parameter (via @AuthenticationPrincipal
    // or `Authentication authentication`) or delegate the lookup to AuthHelperService.
    noClasses()
        .that()
        .resideInAPackage("..backend.controller..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Controllers must read the principal via @AuthenticationPrincipal / "
                + "Authentication parameters, or delegate to AuthHelperService — direct "
                + "SecurityContextHolder access splits the auth contract across the codebase.")
        .check(CLASSES);
  }

  @Test
  void mapperLayerShouldNotReachIntoSecurityContext() {
    // Reasoning: MapStruct mappers are supposed to be pure transformers. Reaching
    // into SecurityContextHolder from a resolver method (as MissionMapper did before
    // the refactor) couples DTO shaping to the request-scoped security context and
    // makes the mapper untestable without a full Spring security setup. If a mapper
    // needs to know "is the caller authenticated / which roles do they have", it
    // must depend on AuthHelperService — never on SecurityContextHolder directly.
    noClasses()
        .that()
        .resideInAPackage("..backend.mapper..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(SECURITY_CONTEXT_HOLDER)
        .because(
            "Mappers must stay pure transformers; route any auth lookup through "
                + "AuthHelperService so the mapper does not depend on the request-scoped "
                + "SecurityContextHolder.")
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
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .should()
        .haveRawReturnType(annotatedWith(JPA_ENTITY))
        .because(
            "Controllers must return DTOs (or Page<Dto>/ResponseEntity<Dto>), never raw JPA"
                + " entities.")
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
        .that()
        .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
        .should(haveAtLeastOnePreAuthorizeAnnotation())
        .because(
            "Every REST controller class must declare at least one @PreAuthorize annotation (either"
                + " on the class or on any method) so it cannot silently bypass authorisation."
                + " Public endpoints should use @PreAuthorize(\"permitAll()\").")
        .check(CLASSES);
  }

  @Test
  void controllerLayerShouldNotDependOnRepositoryLayer() {
    // Reasoning: CLAUDE.md prescribes a strict controller → service → repository layering.
    // A controller injecting a Spring Data repository directly skips the service layer where
    // multi-user data isolation, transactional boundaries and the @PreAuthorize logic live —
    // and once that shortcut exists, it tends to multiply. Forbidding the dependency at the
    // package level (controllers must not even see repositories) makes the layering breach
    // visible at compile time instead of in a long code review.
    noClasses()
        .that()
        .resideInAPackage("..backend.controller..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..backend.repository..")
        .because(
            "Controllers must go through the service layer — a controller depending on a "
                + "repository bypasses @Transactional boundaries, owner filtering and the "
                + "@PreAuthorize seam, all of which live in services.")
        .check(CLASSES);
  }

  @Test
  void controllerMethodsShouldNotExposeJpaEntitiesInGenericWrappers() {
    // Reasoning: complements `controllerMethodsShouldNotReturnJpaEntities()`. That sister rule
    // only inspects the *raw* return type, so a method that returns `ResponseEntity<User>` or
    // `Page<Mission>` slips through — even though the JPA entity still ends up serialised on
    // the wire with all the lazy-loading / column-leak risks CLAUDE.md warns about. This rule
    // walks the actual generic type arguments of the known wrapper types
    // ({@link #ENTITY_GENERIC_WRAPPERS}) and rejects any wrapper carrying a {@code @Entity}.
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .should(notReturnAnEntityInsideAGenericWrapper())
        .because(
            "Controllers must wrap DTOs, never JPA entities, even when the entity is "
                + "tucked inside ResponseEntity<…>/Page<…>/List<…>/Optional<…>/etc.")
        .check(CLASSES);
  }

  @Test
  void mutatingServiceMethodsInReadOnlyClassesNeedExplicitTransactional() {
    // Reasoning: many services declare a class-level @Transactional(readOnly = true) so that
    // their query methods inherit a read-only transaction by default. Mutating methods on such
    // a class MUST override that with their own @Transactional, otherwise the JPA writes
    // either fail (Postgres refuses INSERT/UPDATE under SET TRANSACTION READ ONLY) or, worse,
    // silently no-op because the persistence context is never flushed. Forgetting this
    // override is a subtle bug that does not surface in `application-dev.yml` (some drivers
    // tolerate it) but breaks in prod.
    //
    // The rule fires when:
    //   * the declaring class carries @Transactional(readOnly = true), AND
    //   * the method name starts with a mutating prefix (see MUTATING_METHOD_PREFIXES), AND
    //   * the method itself is not annotated with @Transactional.
    // It does NOT inspect the method body, so the heuristic relies on the project's naming
    // convention (createX/updateX/deleteX/addX/removeX/…). False positives can be silenced
    // by simply annotating the method with @Transactional(readOnly = true) explicitly.
    classes()
        .that()
        .resideInAPackage("..backend.service..")
        .should(declareTransactionalForMutatingMethodsWhenClassIsReadOnly())
        .because(
            "A class-level @Transactional(readOnly = true) silently propagates to every "
                + "method — mutating operations must explicitly override it with their own "
                + "@Transactional, otherwise the write happens in a read-only transaction.")
        .check(CLASSES);
  }

  @Test
  void writeEndpointsMustDeclareAnAuthorisationAnnotation() {
    // Reasoning: tightens `everyRestControllerShouldDeclareAtLeastOneAuthorisationAnnotation`
    // from "the class declares *some* @PreAuthorize" to "every state-changing endpoint
    // (@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping) carries explicit authorisation",
    // either inline on the method or class-wide. The previous, weaker form let a brand-new
    // write endpoint slip through without an explicit decision as long as some other method
    // on the same controller had a @PreAuthorize — exactly the regression case that hides a
    // missing auth check behind an unrelated annotation.
    //
    // Public endpoints are allowed but must be EXPLICIT: annotate them with
    // @PreAuthorize("permitAll()") so the decision is visible at the method level instead of
    // hiding two folders away in SecurityConfig's requestMatchers list.
    methods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAPackage("..backend.controller..")
        .and()
        .arePublic()
        .and(isAnnotatedWithAnyOf(POST_MAPPING, PUT_MAPPING, DELETE_MAPPING, PATCH_MAPPING))
        .should(haveMethodOrClassLevelPreAuthorize())
        .because(
            "Every state-changing HTTP endpoint must carry an explicit @PreAuthorize "
                + "(either method-level or class-level). For deliberately public endpoints "
                + "use @PreAuthorize(\"permitAll()\") so the auth contract stays visible "
                + "next to the handler instead of buried in SecurityConfig.")
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
    return new ArchCondition<JavaClass>(
        "declare @PreAuthorize on the class or on at least one method") {
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
        events.add(
            SimpleConditionEvent.violated(
                clazz,
                clazz.getFullName()
                    + " is a @RestController but declares no @PreAuthorize "
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

  private static DescribedPredicate<JavaMethod> isAnnotatedWithAnyOf(String... annotationFqns) {
    String description = "annotated with any of " + String.join(", ", annotationFqns);
    return new DescribedPredicate<JavaMethod>(description) {
      @Override
      public boolean test(JavaMethod method) {
        for (String fqn : annotationFqns) {
          if (method.isAnnotatedWith(fqn)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  private static ArchCondition<JavaMethod> notReturnAnEntityInsideAGenericWrapper() {
    return new ArchCondition<JavaMethod>("not return a JPA entity inside a known generic wrapper") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        JavaType returnType = method.getReturnType();
        if (!(returnType instanceof JavaParameterizedType parameterized)) {
          return;
        }
        JavaClass rawType = parameterized.toErasure();
        if (!ENTITY_GENERIC_WRAPPERS.contains(rawType.getFullName())) {
          return;
        }
        for (JavaType arg : parameterized.getActualTypeArguments()) {
          JavaClass argClass = arg.toErasure();
          if (argClass.isAnnotatedWith(JPA_ENTITY)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    method.getFullName()
                        + " returns "
                        + rawType.getSimpleName()
                        + "<"
                        + argClass.getSimpleName()
                        + "> — JPA entities must not "
                        + "be exposed through generic wrappers; map to a DTO first."));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass>
      declareTransactionalForMutatingMethodsWhenClassIsReadOnly() {
    return new ArchCondition<JavaClass>(
        "declare method-level @Transactional on mutating methods when the class is"
            + " @Transactional(readOnly = true)") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        if (!isClassReadOnlyTransactional(clazz)) {
          return;
        }
        for (JavaMethod method : clazz.getMethods()) {
          if (!method
              .getModifiers()
              .contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
            continue;
          }
          if (!hasMutatingNamePrefix(method.getName())) {
            continue;
          }
          if (method.isAnnotatedWith(TRANSACTIONAL)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " — declaring class is @Transactional(readOnly = true) "
                      + "but this mutating method has no @Transactional override; writes "
                      + "would happen in a read-only transaction. Annotate the method with "
                      + "@Transactional or rename it to a non-mutating prefix."));
        }
      }
    };
  }

  private static ArchCondition<JavaMethod> haveMethodOrClassLevelPreAuthorize() {
    return new ArchCondition<JavaMethod>(
        "declare @PreAuthorize on the method or on the declaring class") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (method.isAnnotatedWith(PRE_AUTHORIZE)) {
          return;
        }
        if (method.getOwner().isAnnotatedWith(PRE_AUTHORIZE)) {
          return;
        }
        events.add(
            SimpleConditionEvent.violated(
                method,
                method.getFullName()
                    + " — state-changing endpoint without @PreAuthorize. "
                    + "Add @PreAuthorize on the method (or class-level) — use "
                    + "@PreAuthorize(\"permitAll()\") if the endpoint is deliberately public."));
      }
    };
  }

  private static boolean isClassReadOnlyTransactional(JavaClass clazz) {
    if (!clazz.isAnnotatedWith(TRANSACTIONAL)) {
      return false;
    }
    JavaAnnotation<?> annotation = clazz.getAnnotationOfType(TRANSACTIONAL);
    return annotation
        .tryGetExplicitlyDeclaredProperty("readOnly")
        .map(value -> Boolean.TRUE.equals(value))
        .orElse(false);
  }

  private static boolean hasMutatingNamePrefix(String methodName) {
    String lower = methodName.toLowerCase(java.util.Locale.ROOT);
    for (String prefix : MUTATING_METHOD_PREFIXES) {
      if (lower.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
